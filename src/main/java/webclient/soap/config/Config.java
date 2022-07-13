package webclient.soap.config;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.MarshalException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.namespace.QName;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.CodecException;
import org.springframework.core.codec.Encoder;
import org.springframework.core.codec.EncodingException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.PooledDataBuffer;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.r2dbc.connection.init.CompositeDatabasePopulator;
import org.springframework.r2dbc.connection.init.ConnectionFactoryInitializer;
import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator;
import org.springframework.util.ClassUtils;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.ws.WebServiceMessage;
import org.springframework.ws.WebServiceMessageFactory;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.SoapHeader;
import org.springframework.ws.soap.SoapMessage;
import org.springframework.ws.support.DefaultStrategiesHelper;
import org.springframework.xml.transform.StringSource;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import io.r2dbc.spi.ConnectionFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.tcp.TcpClient;
import webclient.soap.client.webservice.PostMessage;
import webclient.soap.encoding.Jaxb2SoapDecoder;
import webclient.soap.encoding.JaxbContextContainer;
import webclient.soap.request.SoapEnvelopeRequest;

@Configuration
public class Config{
	private static final Logger logger = LoggerFactory.getLogger(Config.class);

	@Bean
	public ConnectionFactoryInitializer databaseInitializer(ConnectionFactory connectionFactory) {

		ConnectionFactoryInitializer initializer = new ConnectionFactoryInitializer();
		initializer.setConnectionFactory(connectionFactory);

		CompositeDatabasePopulator populator = new CompositeDatabasePopulator();
		populator.addPopulators(new ResourceDatabasePopulator(new ClassPathResource("schema/schema.sql")));

		initializer.setDatabasePopulator(populator);

		return initializer;
	}

	@Bean
	public WebClient webClient() {
		try {
			TcpClient tcpClient = TcpClient.create();

			tcpClient
			.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
			.doOnConnected(connection -> {
				connection.addHandlerLast(new ReadTimeoutHandler(5000, TimeUnit.MILLISECONDS));
				connection.addHandlerLast(new WriteTimeoutHandler(5000, TimeUnit.MILLISECONDS));
			});

			ExchangeStrategies exchangeStrategies = ExchangeStrategies.builder().codecs( clientCodecConfigurer -> {
				clientCodecConfigurer.customCodecs().register(new Jaxb2SoapEncoder());
				clientCodecConfigurer.customCodecs().register(new Jaxb2SoapDecoder());
			}).build();

			WebClient webClient = WebClient.builder()
					.clientConnector(new ReactorClientHttpConnector(HttpClient.from(tcpClient).wiretap(true)))
					.exchangeStrategies( exchangeStrategies )
					.filter(logRequest())
					.filter(logResponse())
					// .filter(logBody())
					.build();

			return webClient;
		} catch (Exception e) {
			throw e;
		}
	}
	private ExchangeFilterFunction logResponse() {
		return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
			logger.info("Response: {}", clientResponse.statusCode());

			clientResponse.headers().asHttpHeaders()
			.forEach((name, values) -> values.forEach(value -> logger.info("{}={}", name, value)));


			return Mono.just(clientResponse);
		});
	}

	private ExchangeFilterFunction logBody() {

		return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
			if (clientResponse.statusCode() != null && (clientResponse.statusCode().is4xxClientError() || clientResponse.statusCode().is5xxServerError())) {
				return clientResponse.bodyToMono(String.class)
						.flatMap(body -> {
							logger.info("Body is {}", body);						
							return Mono.just(clientResponse);
						});
			} else {
				return Mono.just(clientResponse);
			}
		});
	}
	
	private ExchangeFilterFunction logRequest() {
		return (clientRequest, next) -> {

			logger.info("Request: {} {} {}", clientRequest.method(), clientRequest.url(), clientRequest.body());
			logger.info("--- Http Headers: ---");
			clientRequest.headers().forEach(this::logHeader);
			logger.info("--- Http Cookies: ---");
			clientRequest.cookies().forEach(this::logHeader);
			return next.exchange(clientRequest);
		};
	}

	private void logHeader(String name, List<String> values) {
		values.forEach(value -> logger.info("{}={}", name, value));
	}
	
	public class Jaxb2SoapEncoder implements Encoder<Object> {

		private final JaxbContextContainer jaxbContexts = new JaxbContextContainer();

		@Override
		public boolean canEncode(ResolvableType elementType, MimeType mimeType) {
			Class<?> outputClass = elementType.toClass();
			return (outputClass.isAnnotationPresent(XmlRootElement.class) ||
					outputClass.isAnnotationPresent(XmlType.class));

		}

		@Override
		public Flux<DataBuffer> encode(Publisher<?> inputStream, DataBufferFactory bufferFactory, ResolvableType elementType, MimeType mimeType, Map<String, Object> hints) {
			return Flux.from(inputStream)
					.take(1)
					.concatMap(value -> encode(value, bufferFactory, elementType, mimeType, hints))
					.doOnDiscard(PooledDataBuffer.class, PooledDataBuffer::release);
		}

		@Override
		public List<MimeType> getEncodableMimeTypes() {
			return Arrays.asList( MimeTypeUtils.TEXT_XML );
		}



		private Flux<DataBuffer> encode(Object value ,
				DataBufferFactory bufferFactory,
				ResolvableType type,
				MimeType mimeType,
				Map<String, Object> hints){

			return Mono.fromCallable(() -> {
				boolean release = true;
				DataBuffer buffer = bufferFactory.allocateBuffer(1024);
				try {
					SoapEnvelopeRequest soapEnvelopeRequest = (SoapEnvelopeRequest)value;

					OutputStream outputStream = buffer.asOutputStream();
					Class<?> clazz = ClassUtils.getUserClass(soapEnvelopeRequest.getBody());
					Marshaller marshaller = initMarshaller(clazz);

					DefaultStrategiesHelper helper = new DefaultStrategiesHelper(WebServiceTemplate.class);
					WebServiceMessageFactory messageFactory = helper.getDefaultStrategy(WebServiceMessageFactory.class);

					WebServiceMessage message = messageFactory.createWebServiceMessage();

					if( soapEnvelopeRequest.getHeaderContent() != null ){
						SoapMessage soapMessage = (SoapMessage)message;

						SoapHeader header = soapMessage.getSoapHeader();
						StringSource headerSource = new StringSource(soapEnvelopeRequest.getHeaderContent());
						Transformer transformer = TransformerFactory.newInstance().newTransformer();
						transformer.transform(headerSource, header.getResult());
					}


					marshaller.marshal(new JAXBElement(
							new QName("urn:xmethods-delayed-quotes","post_message"), PostMessage.class, soapEnvelopeRequest.getBody() ),message.getPayloadResult());


					message.writeTo(outputStream);

					release = false;
					return buffer;
				}
				catch (MarshalException ex) {
					throw new EncodingException(
							"Could not marshal " + value.getClass() + " to XML", ex);
				}
				catch (JAXBException ex) {
					throw new CodecException("Invalid JAXB configuration", ex);
				}
				finally {
					if (release) {
						DataBufferUtils.release(buffer);
					}
				}
			}).flux();
		}


		private Marshaller initMarshaller(Class<?> clazz) throws JAXBException {
			Marshaller marshaller = this.jaxbContexts.createMarshaller(clazz);
			marshaller.setProperty(Marshaller.JAXB_ENCODING, StandardCharsets.UTF_8.name());
			return marshaller;
		}
	}
}