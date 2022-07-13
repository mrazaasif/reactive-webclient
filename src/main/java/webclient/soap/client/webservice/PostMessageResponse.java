package webclient.soap.client.webservice;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "post_messageResponse", propOrder = {
	 
})
public class PostMessageResponse {

	@XmlElement(required = true)
	protected Resultcode resultcode;

	public Resultcode getResultcode() {
		return resultcode;
	}

	public void setResultcode(Resultcode resultcode) {
		this.resultcode = resultcode;
	}

}	 
