### Example inventory7db: kafka-hana-connect using debezium record state extraction from MySQL to HANA

This example uses Debezium MySQL connector and HANA connector to copy tables from MySQL to HANA using table change events or CDC events. Concretely, the table change events are extracted from MySQL database by MySQL connector. There change event records are sent to HANA connector, where each record is transformed into a series of normal records by Debezium's [Event Flattening transformation](https://debezium.io/docs/configuration/event-flattening/) and subsequently the corresponding record is inserted, updated, or deleted in the corresponding HANA table.

For further information on Debezium and its MySQL connector, refer to [Debezium documentation](https://debezium.io/documentation/reference/index.html).

#### Prerequisites

- This project is built (or its jar file is available)
- Access to HANA
- Docker

#### Running

This description assumes Docker and Docker-Compose are available on local machine. 

##### Step 1: Build Docker image for kafka-connector-hana

Use the instruction for `examples/persons1db` to build the Docker image.


##### Step 2: Starting Zookeeper, Kafka, Kafka-Connect, MySQL Database and Command Line Client

The docker-compose.yaml file defines zookeeper, kafka, mysql, and connect services. It is noted that Kafka broker uses its advertised host set to `host.docker.internal:9092` assuming this host name is resolvable from the containers and at the host. This allows Kafka broker to be accessed from the container of Kafka-Connect and from the host for inspection.

Run `docker-compose up` to start the containers.

```
$ docker-compose up
Creating network "inventory7db_default" with the default driver
Creating inventory7db_zookeeper_1 ... done
Creating inventory7db_mysql_1     ... done
Creating inventory7db_kafka_1     ... done
Creating inventory7db_connect_1   ... done
Attaching to inventory7db_mysql_1, inventory7db_zookeeper_1, inventory7db_kafka_1, inventory7db_connect_1
...
```

After starting the Docker containers using docker-compose, we can verify whether Kafka-Connect is running using curl.

```
$ curl -i http://localhost:8083/
HTTP/1.1 200 OK
Date: Wed, 09 Sep 2020 22:44:49 GMT
Content-Type: application/json
Content-Length: 91
Server: Jetty(9.4.24.v20191120)

{"version":"2.5.0","commit":"66563e712b0b9f84","kafka_cluster_id":"1NEvm9a4TW2t-f5Jkk4peg"}
$
$ curl -i http://localhost:8083/connectors
HTTP/1.1 200 OK
Date: Wed, 09 Sep 2020 22:45:35 GMT
Content-Type: application/json
Content-Length: 2
Server: Jetty(9.4.24.v20191120)

[]
$
```

The above result shows that Kafka Connect using Kafka 2.5.0 is running and there is no connector deployed.

To start MySQL Command Line client, run the following Docker command.

```
docker run -it --rm --name mysqlterm --rm mysql:5.7 sh -c 'exec mysql -h"host.docker.internal" -P"3306" -u"root" -p"debezium"'

```

This will start the command line client.

```
docker run -it --rm --name mysqlterm --rm mysql:5.7 sh -c 'exec mysql -h"host.docker.internal" -P"3306" -u"root" -p"debezium"'
mysql: [Warning] Using a password on the command line interface can be insecure.
Welcome to the MySQL monitor.  Commands end with ; or \g.
Your MySQL connection id is 2
Server version: 5.7.31-log MySQL Community Server (GPL)

Copyright (c) 2000, 2020, Oracle and/or its affiliates. All rights reserved.

Oracle is a registered trademark of Oracle Corporation and/or its
affiliates. Other names may be trademarks of their respective
owners.

Type 'help;' or '\h' for help. Type '\c' to clear the current input statement.

mysql>
```

This Debezium MySQL Database contains several tables. We will use user `mysqluser` and table `inventory.customers` in this scenario.
To run this scenario, the user needs certain authorization grants. Run the following commands to add the required grants to user `mysqluser`. Subsequently inspect the content of the tables.

```
mysql> show grants for 'mysqluser';
+----------------------------------------------------------+
| Grants for mysqluser@%                                   |
+----------------------------------------------------------+
| GRANT USAGE ON *.* TO 'mysqluser'@'%'                    |
| GRANT ALL PRIVILEGES ON `inventory`.* TO 'mysqluser'@'%' |
+----------------------------------------------------------+
2 rows in set (0.00 sec)

mysql> GRANT RELOAD, SUPER, REPLICATION CLIENT, REPLICATION SLAVE ON *.* TO 'mysqluser';
Query OK, 0 rows affected (0.01 sec)

mysql> show grants for 'mysqluser';
+--------------------------------------------------------------------------------------+
| Grants for mysqluser@%                                                               |
+--------------------------------------------------------------------------------------+
| GRANT RELOAD, SUPER, REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'mysqluser'@'%' |
| GRANT ALL PRIVILEGES ON `inventory`.* TO 'mysqluser'@'%'                             |
+--------------------------------------------------------------------------------------+
2 rows in set (0.00 sec)

mysql> use inventory
Reading table information for completion of table and column names
You can turn off this feature to get a quicker startup with -A

Database changed
mysql> show tables
    -> ;
+---------------------+
| Tables_in_inventory |
+---------------------+
| addresses           |
| customers           |
| geom                |
| orders              |
| products            |
| products_on_hand    |
+---------------------+
6 rows in set (0.00 sec)

mysql> SELECT * FROM customers;
+------+------------+-----------+-----------------------+
| id   | first_name | last_name | email                 |
+------+------------+-----------+-----------------------+
| 1001 | Sally      | Thomas    | sally.thomas@acme.com |
| 1002 | George     | Bailey    | gbailey@foobar.com    |
| 1003 | Edward     | Walker    | ed@walker.com         |
| 1004 | Anne       | Kretchmar | annek@noanswer.org    |
+------+------------+-----------+-----------------------+
4 rows in set (0.00 sec)
```


##### Step 3: Installing MySQL and HANA connectors

We prepare for the connector json files using the json files `connect-mysql-source-7.json` and `connect-hana-sink-7.json`. Adjust the connection properties of  `connect-hana-sink-7.json`.

```
{
    "name": "inventory-hana-sink",
    "config": {
        "connector.class": "com.sap.kafka.connect.source.hana.HANASourceConnector",
        "tasks.max": "1",
        "topics": "dbserver1.inventory.customers",
        "connection.url": "jdbc:sap://<host>/",
        "connection.user": "${file:/kafka/custom-config/secrets.properties:connection1-user}",
        "connection.password": "${file:/kafka/custom-config/secrets.properties:connection1-password}",
        "transforms": "unwrap",
        "transforms.unwrap.type": "io.debezium.transforms.ExtractNewRecordState",
        "transforms.unwrap.drop.tombstones": "false",
        "transforms.unwrap.delete.handling.mode": "rewrite",
        "auto.create": "true",
        "dbserver1.inventory.customers.insert.mode": "upsert",
        "dbserver1.inventory.customers.delete.enabled": "false",
        "dbserver1.inventory.customers.pk.fields": "id",
        "dbserver1.inventory.customers.pk.mode": "record_key",
        "dbserver1.inventory.customers.table.name": "\"<schemaname>\".\"INVENTORY_CUSTOMERS\""
    }
}
```

The above configuration uses Debezium's Event Flattening SMT https://debezium.io/docs/configuration/event-flattening/ to convert the event records to the annotated records that can interpreted by HANA connector. The insert mode `insert.mode` must be set to `upsert` and the primary keys must be specified in `pk.fields` using `pk.mode`. When `delete.enabled` is set to false, the deletion of a record will only mark the record as deleted. When `delete.enabled` is set to true, the deletion of a record will delete the record.

We deploy the connectors by posting the connector configuration json files to the Kafka Connect's API.

```
$ curl -i -X POST -H "Content-Type:application/json" -d @connect-mysql-source-7.json http://localhost:8083/connectors/
HTTP/1.1 201 Created
Date: Tue, 12 Jan 2021 22:29:00 GMT
Location: http://localhost:8083/connectors/inventory-mysql-source
Content-Type: application/json
Content-Length: 630
Server: Jetty(9.4.24.v20191120)

{"name":"inventory-mysql-source","config":{"connector.class":"io.debezium.connector.mysql.MySqlConnector","tasks.max":"1","database.hostname":"mysql","database.port":"3306","database.user":"${file:/kafka/custom-config/tmp-secrets.properties:connection2-user}","database.password":"${file:/kafka/custom-config/tmp-secrets.properties:connection2-password}","database.server.id":"184054","d
...
$
$ curl -i -X POST -H "Accept:application/json" -H "Content-Type:application/json" -d @tmp-connect-hana-sink-7.json http://localhost:8083/connectors/
HTTP/1.1 201 Created
Date: Tue, 12 Jan 2021 22:30:10 GMT
Location: http://localhost:8083/connectors/inventory-hana-sink
Content-Type: application/json
Content-Length: 764
Server: Jetty(9.4.24.v20191120)

{"name":"inventory-hana-sink","config":{"connector.class":"com.sap.kafka.connect.sink.hana.HANASinkConnector","tasks.max":"1","topics":"dbserver1.inventory.customers","connection.url":"jdbc:sap://...
$
$ curl http://localhost:8083/connectors/
["inventory-mysql-source","inventory-hana-sink"]
$
```

The above result shows that the connectors are deployed.

##### Step 5: Interactively update the MySQL Table and verify the result in HANA Table

After starting the connectors, you will find table `inventory_customers` in HANA. The replicated HANA table has the additional column `__deleted`. When the connector is configured with `delete.enabled=false`, the deleted record will be kept but the value of this column will be set to true. In contrast, when the connector is configured with `delete.enabled=true`, the deleted record will be deleted.

```
select * from inventory_customers;
  id  first_name  last_name  email                  __deleted
----  ----------  ---------  ---------------------  ---------
1001  Sally       Thomas     sally.thomas@acme.com  false    
1002  George      Bailey     gbailey@foobar.com     false    
1003  Edward      Walker     ed@walker.com          false    
1004  Anne        Kretchmar  annek@noanswer.org     false    
```

At MySQL Command Line Client,  run the following SQL to insert a new record.
```
mysql> INSERT INTO customers VALUES (default, "Sarah", "Thompson", "kitt@acme.com");
Query OK, 1 row affected (0.00 sec)

mysql> select * from customers;
+------+------------+-----------+-----------------------+
| id   | first_name | last_name | email                 |
+------+------------+-----------+-----------------------+
| 1001 | Sally      | Thomas    | sally.thomas@acme.com |
| 1002 | George     | Bailey    | gbailey@foobar.com    |
| 1003 | Edward     | Walker    | ed@walker.com         |
| 1004 | Anne       | Kretchmar | annek@noanswer.org    |
| 1005 | Sarah      | Thompson  | kitt@acme.com         |
+------+------------+-----------+-----------------------+
5 rows in set (0.00 sec)

mysql> 
```

At HANA, verify the table to have this record added.
```
select * from inventory_customers;
  id  first_name  last_name  email                  __deleted
----  ----------  ---------  ---------------------  ---------
1001  Sally       Thomas     sally.thomas@acme.com  false    
1002  George      Bailey     gbailey@foobar.com     false    
1003  Edward      Walker     ed@walker.com          false    
1004  Anne        Kretchmar  annek@noanswer.org     false    
1005  Sarah       Thompson   kitt@acme.com          false    
```

At MySQL Command Line Client,  run the following SQL to update one record.

```
mysql> UPDATE customers SET first_name='Anne Marie' WHERE id=1004;
Query OK, 1 row affected (0.02 sec)
Rows matched: 1  Changed: 1  Warnings: 0

mysql> select * from customers;
+------+------------+-----------+-----------------------+
| id   | first_name | last_name | email                 |
+------+------------+-----------+-----------------------+
| 1001 | Sally      | Thomas    | sally.thomas@acme.com |
| 1002 | George     | Bailey    | gbailey@foobar.com    |
| 1003 | Edward     | Walker    | ed@walker.com         |
| 1004 | Anne Marie | Kretchmar | annek@noanswer.org    |
| 1005 | Sarah      | Thompson  | kitt@acme.com         |
+------+------------+-----------+-----------------------+
5 rows in set (0.00 sec)

mysql> 
```

At HANA, verify the updated table.
```
select * from inventory_customers;
  id  first_name  last_name  email                  __deleted
----  ----------  ---------  ---------------------  ---------
1001  Sally       Thomas     sally.thomas@acme.com  false    
1002  George      Bailey     gbailey@foobar.com     false    
1003  Edward      Walker     ed@walker.com          false    
1005  Sarah       Thompson   kitt@acme.com          false    
1004  Anne Marie  Kretchmar  annek@noanswer.org     false
```

At MySQL Command Line Client,  run the following SQL to delete one record.

```
mysql> DELETE FROM customers WHERE id=1005;
Query OK, 1 row affected (0.01 sec)

mysql> select * from customers;
+------+------------+-----------+-----------------------+
| id   | first_name | last_name | email                 |
+------+------------+-----------+-----------------------+
| 1001 | Sally      | Thomas    | sally.thomas@acme.com |
| 1002 | George     | Bailey    | gbailey@foobar.com    |
| 1003 | Edward     | Walker    | ed@walker.com         |
| 1004 | Anne Marie | Kretchmar | annek@noanswer.org    |
+------+------------+-----------+-----------------------+
4 rows in set (0.00 sec)

mysql> 
```

At HANA, verify the updated table. Depending on the connector's `delete.enabled` value, the deleted record will be only marked as deleted or deleted, as shown below.

When `deleted.enabled` is set to false
```
select * from inventory_customers;
  id  first_name  last_name  email                  __deleted
----  ----------  ---------  ---------------------  ---------
1001  Sally       Thomas     sally.thomas@acme.com  false    
1002  George      Bailey     gbailey@foobar.com     false    
1003  Edward      Walker     ed@walker.com          false    
1004  Anne Marie  Kretchmar  annek@noanswer.org     false    
1005  Sarah       Thompson   kitt@acme.com          true
```

When `deleted.enabled` is set to true
```
select * from inventory_customers;
  id  first_name  last_name  email                  __deleted
----  ----------  ---------  ---------------------  ---------
1001  Sally       Thomas     sally.thomas@acme.com  false    
1002  George      Bailey     gbailey@foobar.com     false    
1003  Edward      Walker     ed@walker.com          false    
1004  Anne Marie  Kretchmar  annek@noanswer.org     false
```

##### Step 6: Shut down

Use `docker-compose down` to shutdown the containers.

```
$ docker-compose down
Stopping inventory7db_connect_1   ... done
Stopping inventory7db_kafka_1     ... done
Stopping inventory7db_zookeeper_1 ... done
Stopping inventory7db_mysql_1     ... done
Removing inventory7db_connect_1   ... done
Removing inventory7db_kafka_1     ... done
Removing inventory7db_zookeeper_1 ... done
Removing inventory7db_mysql_1     ... done
Removing network inventory7db_default
$ 
```
