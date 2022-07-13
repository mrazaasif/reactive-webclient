package webclient.soap.controller;

 
import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.soap.SOAPException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;
import webclient.soap.client.IReactiveSoapClient;
import webclient.soap.dto.IncomingDataDTO;

@RestController
public class Controller {
	
	private static final Logger logger = LoggerFactory.getLogger(Controller.class);
	
	@Autowired
    private IReactiveSoapClient reactiveSoapClient;

    
    @PostMapping(value = "/checkclient")
    public Mono<Integer> callClient( @RequestBody IncomingDataDTO incomingDataDTO) throws SOAPException, ParserConfigurationException, IOException {
    	logger.info("IncomingDataDTO : "+ incomingDataDTO);
                 
        return reactiveSoapClient.call(incomingDataDTO) ;
    }

 
}
