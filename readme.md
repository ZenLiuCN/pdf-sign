# sign


## config

```shell
    java -Dsign.port=8080 -jar sign.jar 
```

## proto
```shell
POST http(s)://host:port/sign 
```
body format
```text
INTEGER[LITTLE_ENDIAN] # bytes count of pdf file
BYTES # bytes of pdf file

INTEGER[LITTLE_ENDIAN] # bytes count of sign picture, zero for none
BYTES # bytes of signature picture

INTEGER[LITTLE_ENDIAN] # bytes count of sign keyword, zero for none
BYTES # bytes of signature keyword utf8 code

INTEGER[LITTLE_ENDIAN] # bytes count of seal picture, zero for none
BYTES # bytes of seal picture

INTEGER[LITTLE_ENDIAN] # bytes count of seal keyword, zero for none
BYTES # bytes of seal keyword utf8 code

INTEGER[LITTLE_ENDIAN] # bytes count of date, zero for none
BYTES # bytes of date utf8 code

INTEGER[LITTLE_ENDIAN] # bytes count of date keyword, zero for none
BYTES # bytes of date keyword utf8 code

INTEGER[LITTLE_ENDIAN] # entries of form fields, zero for none

INTEGER[LITTLE_ENDIAN] # bytes count of entry key
BYTES # bytes of entry key utf8 code
INTEGER[LITTLE_ENDIAN] # bytes count of entry value
BYTES # bytes of entry value utf8 code

...... # repeat of entries

```