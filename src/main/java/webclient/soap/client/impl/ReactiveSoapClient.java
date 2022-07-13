package webclient.soap.client.impl;

import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.soap.SOAPException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;
import webclient.soap.client.IReactiveSoapClient;
import webclient.soap.client.webservice.Message;
import webclient.soap.client.webservice.PostMessage;
import webclient.soap.client.webservice.PostMessageResponse;
import webclient.soap.dto.IncomingDataDTO;
import webclient.soap.request.SoapEnvelopeRequest;
import webclient.soap.respositories.MessageRepository;
import webclient.soap.vo.MessageVO;

@Component
public class ReactiveSoapClient implements IReactiveSoapClient {

	private static final Logger logger = LoggerFactory.getLogger(ReactiveSoapClient.class);

	private WebClient webClient;
	private String soapServiceUrl;

	@Autowired
	MessageRepository messageRepository;

	public ReactiveSoapClient(WebClient webClient, @Value("${soap.service.url}") String soapServiceUrl) {
		this.webClient = webClient;
		this.soapServiceUrl = soapServiceUrl;
	}

	public Mono<Integer> call(IncomingDataDTO incomingDataDTO)
			throws SOAPException, ParserConfigurationException, IOException {
		
		if(webClient == null)
			throw new SOAPException("Webservice offline");

		Assert.notNull(incomingDataDTO, "incomingDataDTO  must not null");
		Integer id = incomingDataDTO.getId();
		Assert.notNull(id, "id must not null");
		String incomingMessage = incomingDataDTO.getMessage();
		Assert.notNull(incomingMessage, "incomingMessage  must not null");	
		
		MessageVO messageVO = new MessageVO();
		messageVO.setMessage(incomingMessage);
	    Mono<MessageVO> messageMono = messageRepository.save(messageVO);
		
		PostMessage postMessage = prepareSoapMessage(incomingMessage);
		SoapEnvelopeRequest soapEnvelopeRequest = new SoapEnvelopeRequest(null, postMessage);

		Mono<PostMessageResponse> postMessageResponseMono = webClient.post().uri(soapServiceUrl)
				.contentType(MediaType.TEXT_XML)
				.body(Mono.just(soapEnvelopeRequest), SoapEnvelopeRequest.class).retrieve()
				.onStatus(HttpStatus::isError,
						clientResponse -> clientResponse.bodyToMono(String.class)
								.flatMap(errorResponseBody -> Mono.error(
										new ResponseStatusException(clientResponse.statusCode(), errorResponseBody))))

				.bodyToMono(PostMessageResponse.class).doOnSuccess((PostMessageResponse s) -> {
				 
					
				}).flatMap(postMessageResponse -> Mono.deferContextual(ctx -> {
					IncomingDataDTO savedIncomingDataDTO = ctx.get("incomingDataDTO");
					logger.info("Result Received against (" + savedIncomingDataDTO + ")::" + postMessageResponse + "::resultcode::"
							+ (postMessageResponse != null && postMessageResponse.getResultcode() != null ? postMessageResponse.getResultcode().getValue() : "empty"));
					
					
					return Mono.fromCallable(() -> postMessageResponse);

				})).doOnError(ResponseStatusException.class, error -> {
					logger.error("error : " + error);
				}).doOnError(Exception.class, (Exception error) -> {
					logger.error("error : " + error);
					error.printStackTrace();
				}).contextWrite( ctx-> ctx.put("incomingDataDTO", incomingDataDTO));

		return postMessageResponseMono.zipWith(messageMono, (postMessageResponse, savedMessageVO) -> {
			logger.info("postMessageResponse::" + postMessageResponse + "::return Id::" + savedMessageVO.getId());
			return savedMessageVO.getId();
		});

	}

	public PostMessage prepareSoapMessage(String incomingMessage) {
		Message message = new Message();
		message.setValue(incomingMessage);
		 
		PostMessage postMessage = new PostMessage();
		postMessage.setMessage(message);
		return postMessage;
	}

}
