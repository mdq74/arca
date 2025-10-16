
package ar.gov.afip.wsaa;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Clase Java para anonymous complex type.
 * 
 * <p>El siguiente fragmento de esquema especifica el contenido que se espera que haya en esta clase.
 * 
 * <pre>
 * &lt;complexType&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="loginCmsReturn" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "loginCmsReturn"
})
@XmlRootElement(name = "loginCmsResponse")
public class LoginCmsResponse {

    @XmlElement(required = true)
    protected String loginCmsReturn;

    /**
     * Obtiene el valor de la propiedad loginCmsReturn.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getLoginCmsReturn() {
        return loginCmsReturn;
    }

    /**
     * Define el valor de la propiedad loginCmsReturn.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setLoginCmsReturn(String value) {
        this.loginCmsReturn = value;
    }

}
