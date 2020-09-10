FROM strimzi/kafka:0.19.0-kafka-2.4.1
USER root:root

RUN mkdir -p /opt/kafka/plugins/kafka-connect-hana
COPY ./target/ /opt/kafka/plugins/kafka-connect-hana/

USER 1001
