FROM ubuntu:xenial

# Install packages
RUN apt-get update
RUN apt-get install -y curl

# Entrypoint script
COPY ./docker/pushgateway-tester/files/entrypoint.sh /
RUN chmod +x entrypoint.sh
ENTRYPOINT /bin/bash entrypoint.sh