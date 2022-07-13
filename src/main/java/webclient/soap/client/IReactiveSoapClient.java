package webclient.soap.client;

import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.soap.SOAPException;

import reactor.core.publisher.Mono;
import webclient.soap.dto.IncomingDataDTO;

public interface IReactiveSoapClient {
	public Mono<Integer> call(IncomingDataDTO incomingDataDTO)
			throws SOAPException, ParserConfigurationException, IOException;
}
