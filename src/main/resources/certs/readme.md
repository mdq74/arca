
🔐 3. Cómo hacerlo paso a paso

Generar nueva clave privada y CSR

openssl genrsa -out arca-java.key 2048
openssl req -new -key arca-java.key -out arca-java.csr \
  -subj "/C=AR/ST=Ciudad de Buenos Aires/L=Ciudad/O=ADEA ADMINISTRADORA DE ARCHIVOS S.A./OU=IT/CN=ARCA-JAVA/emailAddress=diego.marsili@addoc.com/serialNumber=CUIT:30-68233570-6"


En el CN (Common Name), poné algo identificativo:
CN=ARCA-JAVA, O=TuEmpresa, C=AR, serialNumber=CUIT 30XXXXXXXXX

Solicitar nuevo certificado en AFIP

Entrás al portal de AFIP con el Representante Legal.

Vas a Administración de Certificados Digitales → seleccionás tu alias técnico (si ya tenés) o creás uno nuevo.

Subís el CSR (arca-java.csr).

Descargás el .crt emitido y validar las huellas deben coincidir
openssl x509 -noout -modulus -in validarcae_62e86fd805ab32f0.crt | openssl md5
openssl rsa  -noout -modulus -in arca-java.key | openssl md5

diego.marsili@NT-32728 MINGW64 /c/git/arca/arca/src/main/resources/certs (master)
$ openssl x509 -noout -modulus -in validarcae_62e86fd805ab32f0.crt | openssl md5
MD5(stdin)= 0c9776522010d1144d68fc454586d7d8

diego.marsili@NT-32728 MINGW64 /c/git/arca/arca/src/main/resources/certs (master)
$ openssl rsa  -noout -modulus -in arca-java.key | openssl md5
MD5(stdin)= 0c9776522010d1144d68fc454586d7d8

Combinar el nuevo .cer con la clave privada

openssl pkcs12 -export -inkey arca-java.key -in validarcae_62e86fd805ab32f0.crt -out arca-java.pfx -name "arca" -password pass:AddocArca2025!

Configurar tu aplicación Java
En tu app Java (cliente del web service AFIP, ej. WSFEv1):

System.setProperty("javax.net.ssl.keyStore", "ruta/arca-java.p12");
System.setProperty("javax.net.ssl.keyStorePassword", "tu_pass");
System.setProperty("javax.net.ssl.keyStoreType", "PKCS12");

