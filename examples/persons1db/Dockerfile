FROM debezium/connect:1.7.0.Final
USER root:root

RUN mkdir -p /kafka/connect/kafka-connector-hana
COPY ./target/ /kafka/connect/kafka-connector-hana/

USER 1001
