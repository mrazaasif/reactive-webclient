# Set external web service url in application.properties file in the following tag:

# soap.service.url=<url_to_external_service>

# Set port in the following tag:

# server.port=<web_client_port> //default port is set to 5810

# To test the client call the following url with POST request

# http://localhost:5810/reactive-webclient/checkclient

# Sample Request:
# {
#    "id": 1,
#    "message": "test message"
# }
