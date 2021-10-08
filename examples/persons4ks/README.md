### Example persons4ks: kafka-hana-connect using strimzi kafka images in kubernetes

This example is a kubernetes version of example [persons4ds](../persons4ds/README.md).

#### Prerequisites

- example persons4ds is built
- Access to HANA
- Access to Kubernetes cluster (e.g. [Minikube](../persons1ks/minikube.md))

#### Running

This description assumes Docker and Kubernetes CLI (`kubectl`) are available on local machine and a Kubernetes cluster is available.

##### Step 1: Prepare Docker image for kafka-connector-hana

We use the Docker image built in example [persons1ds](../persons1ds/README.md). To make this image available to the Kubernetes cluster, push the image to the Docker regisry.

Make sure that `DOCKER_REGISTRY` is set to the registry used (e.g., `docker.io/<username>` for Docker Hub, `kubernetes.docker.internal:5000` when using a local registry) 

If you want to tag the image and push it to the registry, use `make docker_tag` and `make docker_push` of the example folder.

```
$ cd ../persons4ds
$ echo $DOCKER_REGISTRY
kubernetes.docker.internal:5000
$ make docker_tag
Tagging docker image ...
docker tag strimzi-connector-hana-rega kubernetes.docker.internal:5000/strimzi-connector-hana-rega:latest
$ make docker_push
Pushing docker image ...
docker push kubernetes.docker.internal:5000/strimzi-connector-hana-rega:latest
The push refers to repository [kubernetes.docker.internal:5000/strimzi-connector-hana-rega]
56de5af4fe79: Layer already exists
...
latest: digest: sha256:48930655ad18431f7405f5338e8e1656f5be8cc20f888b3f22b327891e091808 size: 4918
$
$ cd ../persons4ks
```
If you are usign minikube and want to make the image directly available at the cluster, refer to [Minikube](../persons1ks/minikube.md).

##### Step 2: Prepare Kubernetes cluster for kafka-connector-hana

Follow step 2 of [persons1ks](../persons1ks/README.md)

##### Step 3: Install Kafka, Zookeeper, Apicurio Schema-Registry for kafka-connector-hana

Follow step 3o of [persons1ks](../persons1ks/README.md) to install Kafka and Zookeeper.

Install Apicurio Schema-Registry by applying file `apicurio-registry.yaml`.

```
$ kubectl apply -f apicurio-registry.yaml -n kafka
deployment.apps/my-cluster-schema-registry created
service/my-cluster-schema-registry-api created
$
```

##### Step 4: Kafka-Connect for kafka-connector-hana

Follow step 4 of [persons1ks](../persons1ks/README.md) to install the credential for HANA.

Install Kafka connect by applying file `kafka-connect-hana-rega.yaml`.
Make sure to adjust the image property value to match the name of the image created in Step 1.

```
$ sed -i'' -e "s/image: kubernetes.docker.internal:5000\/strimzi-connector-hana-rega/image: $DOCKER_REGISTRY\/strimzi-connector-hana-rega:$DOCKER_TAG/" kafka-connect-hana-rega.yaml
$
```

```
$ kubectl apply -f kafka-connect-hana-rega.yaml
kafkaconnect.kafka.strimzi.io/my-connect-cluster created
$
$ kubectl get po -n kafka
NAME                                          READY   STATUS    RESTARTS   AGE
my-cluster-entity-operator-7f8cd6f7fc-sfr8x   3/3     Running   0          3m47s
my-cluster-kafka-0                            2/2     Running   0          4m12s
my-cluster-schema-registry-6b864c5b4-7jjfq    1/1     Running   0          4m29s
my-cluster-zookeeper-0                        1/1     Running   0          4m42s
my-connect-cluster-connect-5d9b68fdb5-7rt2b   1/1     Running   0          89s
strimzi-cluster-operator-68b6d59f74-vrx47     1/1     Running   1          3d22h
$
$ kubectl get svc -n kafka
NAME                             TYPE        CLUSTER-IP      EXTERNAL-IP   PORT(S)                      AGE
my-cluster-kafka-bootstrap       ClusterIP   10.103.134.138   <none>        9091/TCP,9092/TCP,9093/TCP   4m21s
my-cluster-kafka-brokers         ClusterIP   None             <none>        9091/TCP,9092/TCP,9093/TCP   4m21s
my-cluster-schema-registry-api   ClusterIP   10.108.5.129     <none>        8080/TCP                     4m38s
my-cluster-zookeeper-client      ClusterIP   10.100.146.224   <none>        2181/TCP                     4m51s
my-cluster-zookeeper-nodes       ClusterIP   None             <none>        2181/TCP,2888/TCP,3888/TCP   4m51s
my-connect-cluster-connect-api   ClusterIP   10.103.61.217    <none>        8083/TCP                     98s
$
```

##### Step 5: Installing HANA connectors

Follow step 5 of [persons1ks](../persons1ks/README.md) to configure port-forwarding and verify Kafka Connect is running.

We prepare for the connector json files `connect-hana-source-4.json` and `connect-hana-sink-4.json` which are similar to the files created for example [persons4ds](../persons4ds/README.md) but use the different registry address  `my-cluster-schema-registry-api:8080`.

```
{
    "name": "test-topic-4-source",
    "config": {
    ...
        "connection.user": "${file:/opt/kafka/external-configuration/hana-secrets/hana-secrets.properties:connection1-user}",
        "connection.password": "${file:/opt/kafka/external-configuration/hana-secrets/hana-secrets.properties:connection1-password}",
    ...
        "value.converter": "io.apicurio.registry.utils.converter.AvroConverter",
        "value.converter.apicurio.registry.url": "http://registry:8080/apis/registry/v2",
        "value.converter.apicurio.registry.auto-register": "true",
        "value.converter.apicurio.registry.find-latest": "true"
    }
}
```

```
{
    "name": "test-topic-4-sink",
    "config": {
    ...
        "connection.user": "${file:/opt/kafka/external-configuration/hana-secrets/hana-secrets.properties:connection1-user}",
        "connection.password": "${file:/opt/kafka/external-configuration/hana-secrets/hana-secrets.properties:connection1-password}",
    ...
        "value.converter": "io.apicurio.registry.utils.converter.AvroConverter",
        "value.converter.apicurio.registry.url": "http://registry:8080/apis/registry/v2",
        "value.converter.apicurio.registry.auto-register": "true",
        "value.converter.apicurio.registry.find-latest": "true"
    }
}
```

##### Step 6: Verifying the result (Follow Step 6 of example [persions1](../persons1/README.md))

You can connect to the Kafka broker pod to directly inspect the topic or verify the target HANA table.

```
$ kubectl exec -it my-cluster-kafka-0 -n kafka -- bash
Defaulting container name to kafka.
Use 'kubectl describe pod/my-cluster-kafka-0 -n kafka' to see all of the containers in this pod.
[kafka@my-cluster-kafka-0 kafka]$ ls
LICENSE                            kafka_connect_run.sh                                kafka_run.sh
NOTICE                             kafka_connect_tls_prepare_certificates.sh           kafka_tls_prepare_certificates.sh
bin                                kafka_liveness.sh                                   libs
broker-certs                       kafka_mirror_maker_2_connector_config_generator.sh  s2i
client-ca-certs                    kafka_mirror_maker_2_run.sh                         set_kafka_gc_options.sh
cluster-ca-certs                   kafka_mirror_maker_consumer_config_generator.sh     site-docs
config                             kafka_mirror_maker_liveness.sh                      to_bytes.gawk
custom-config                      kafka_mirror_maker_producer_config_generator.sh     zookeeper_config_generator.sh
dynamic_resources.sh               kafka_mirror_maker_run.sh                           zookeeper_healthcheck.sh
kafka_config_generator.sh          kafka_mirror_maker_tls_prepare_certificates.sh      zookeeper_run.sh
kafka_connect_config_generator.sh  kafka_pre_start.sh                                  zookeeper_tls_prepare_certificates.sh
[kafka@my-cluster-kafka-0 kafka]$
```

Use `kafka-topics.sh` to see topic `test_topic_4` is present.

```
[kafka@my-cluster-kafka-0 kafka]$ bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
__consumer_offsets
connect-cluster-configs
connect-cluster-offsets
connect-cluster-status
test_topic_1
test_topic_4
[kafka@my-cluster-kafka-0 kafka]$
```

Use `kafka-console-consumer.sh` to fetch some messages.

```
[kafka@my-cluster-kafka-0 kafka]$ bin/kafka-console-consumer.sh --bootstrap-server  localhost:9092 --topic test_topic_4 --from-beginning
simpson
homer
simpson
marge
simpsobart
simpsolisa

simpson
       maggie
...
```

You can verify that the schema is stored in the registry

```
[kafka@my-cluster-kafka-0 kafka]$ curl -i http://my-cluster-schema-registry-api:8080/api/artifacts
HTTP/1.1 200 OK
Date: Fri, 27 Nov 2020 15:49:32 GMT
Expires: Thu, 26 Nov 2020 15:49:32 GMT
Pragma: no-cache
Cache-control: no-cache, no-store, must-revalidate
Content-Type: application/json
Content-Length: 22

["test_topic_4-value"]
[kafka@my-cluster-kafka-0 kafka]$
[kafka@my-cluster-kafka-0 kafka]$ curl -i http://my-cluster-schema-registry-api:8080/api/artifacts/test_topic_4-value
HTTP/1.1 200 OK
Date: Fri, 27 Nov 2020 15:49:52 GMT
Expires: Thu, 26 Nov 2020 15:49:52 GMT
Pragma: no-cache
Cache-control: no-cache, no-store, must-revalidate
Content-Type: application/json
Content-Length: 240

{"type":"record","name":"d025803persons4","fields":[{"name":"PERSONID","type":"int"},{"name":"LASTNAME","type":["null","string"],"default":null},{"name":"FIRSTNAME","type":["null","string"],"default":null}],"connect.name":"d025803persons4"}
[kafka@my-cluster-kafka-0 kafka]$
```

##### Step 7: Shut down

Use `kubectl delete` to uninstall the resources.

```
$ kubectl delete -f kafka-connect-hana-rega.yaml -n kafka
kafkaconnect.kafka.strimzi.io "my-connect-cluster" deleted
$ kubectl delete -f apicurio-registry.yaml -n kafka
deployment.apps "my-cluster-schema-registry" deleted
service "my-cluster-schema-registry-api" deleted
$ kubectl delete -f kafka-ephemeral-single.yaml -n kafka 
kafka.kafka.strimzi.io "my-cluster" deleted
$
$ kubectl get po -n kafka                                                   
NAME                                        READY   STATUS        RESTARTS   AGE
strimzi-cluster-operator-68b6d59f74-vrx47   1/1     Running       1          3d22h
$
```
