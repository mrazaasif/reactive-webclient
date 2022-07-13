package webclient.soap.respositories;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import webclient.soap.vo.MessageVO;

public interface MessageRepository extends ReactiveCrudRepository<MessageVO, Long> {


}
