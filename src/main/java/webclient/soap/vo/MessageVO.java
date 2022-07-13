package webclient.soap.vo;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("MESSAGE")
public class MessageVO {
	private Integer id;
    private String message;
  
	public MessageVO() {
		 
	}

	@Id
	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	@Override
	public String toString() {
		return "MessageVO [id=" + id + ", message=" + message + "]";
	}
	
	
	

}
