
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

Unir certificados
En 1 solo archivo unir el crt que se baja de afip con los pem de AFIPRoot y Computadores

openssl pkcs12 -export -inkey arca-java.key -in addoc_completo.pem -out arca-java.p12 -name "arca" -password pass:AddocArca2025!

Validar salida
openssl pkcs12 -info -in arca-java.p12 -nokeys


Configurar tu aplicación Java
wsaa.service=wsfe
wsaa.p12Path=src/main/resources/certs/arca-java.pfx
wsaa.p12Password=AddocArca2025!
wsaa.p12Alias=arca
wsaa.renewMinutesBeforeExpiry=10
