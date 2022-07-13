package webclient.soap.dto;

public class IncomingDataDTO {

    Integer id;
    String message;
    
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
		return "IncomingDataDTO [id=" + id + ", message=" + message + "]";
	}
}
