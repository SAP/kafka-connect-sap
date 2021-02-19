### Example inventory8db: kafka-hana-connect using debezium record state extraction from Postgres to HANA

This example uses Debezium Postgres connector and HANA connector to copy tables from Postgres to HANA using table change events or CDC events. As this example is identical scenario to `inventory7db` which uses MySQL instead, some details are omitted in this description. Please find a more full description at `inventory7db`.

#### Prerequisites

- This project is built (or its jar file is available)
- Access to HANA
- Docker

#### Running

This description assumes Docker and Docker-Compose are available on local machine. 

##### Step 1: Build Docker image for kafka-connector-hana

Use the instruction for `examples/persons1db` to build the Docker image.


##### Step 2: Starting Zookeeper, Kafka, Kafka-Connect, Postgres Database

Run `docker-compose up` to start the containers.

```
$ docker-compose up
Creating network "inventory8db_default" with the default driver
Creating volume "inventory8db_custom-config" with default driver
Creating inventory8db_postgres_1  ... done
Creating inventory8db_zookeeper_1 ... done
Creating inventory8db_kafka_1     ... done
Creating inventory8db_connect_1   ... done
Attaching to inventory8db_postgres_1, inventory8db_zookeeper_1, inventory8db_kafka_1, inventory8db_connect_1
...
```

To start Postgres Command Line client, run the following Docker command.

```
$ docker-compose exec postgres env PGOPTIONS="--search_path=inventory" bash -c 'psql -U $POSTGRES_USER postgres'

```

This will start the command line client.

```
docker-compose exec postgres env PGOPTIONS="--search_path=inventory" bash -c 'psql -U $POSTGRES_USER postgres'
psql (11.10 (Debian 11.10-1.pgdg90+1))
Type "help" for help.

postgres=#
```

This Debezium Postgres Database contains several tables. We will use user `postgres` and table `inventory.customers` in this scenario.

```
postgres=# show search_path;
 search_path 
-------------
 inventory
(1 row)

postgres=# select * from customers;
  id  | first_name | last_name |         email         
------+------------+-----------+-----------------------
 1001 | Sally      | Thomas    | sally.thomas@acme.com
 1002 | George     | Bailey    | gbailey@foobar.com
 1003 | Edward     | Walker    | ed@walker.com
 1004 | Anne       | Kretchmar | annek@noanswer.org
(4 rows)

postgres=# 
```


##### Step 3: Installing Postgres and HANA connectors

We prepare for the connector json files using the json files `connect-postgres-source-8.json` and `connect-hana-sink-8.json`. Adjust the connection properties of  `connect-hana-sink-8.json`.

```
{
    "name": "inventory8-hana-sink",
    "config": {
        "connector.class": "com.sap.kafka.connect.sink.hana.HANASinkConnector",
        "tasks.max": "1",
        "topics": "dbserver2.inventory.customers",
        "connection.url": "jdbc:sap://<host>/",
        "connection.user": "${file:/kafka/custom-config/secrets.properties:connection1-user}",
        "connection.password": "${file:/kafka/custom-config/secrets.properties:connection1-password}",
        "transforms": "unwrap",
        "transforms.unwrap.type": "io.debezium.transforms.ExtractNewRecordState",
        "transforms.unwrap.drop.tombstones": "false",
        "transforms.unwrap.delete.handling.mode": "rewrite",
        "auto.create": "true",
        "dbserver2.inventory.customers.insert.mode": "upsert",
        "dbserver2.inventory.customers.delete.enabled": "false",
        "dbserver2.inventory.customers.pk.fields": "id",
        "dbserver2.inventory.customers.pk.mode": "record_key",
        "dbserver2.inventory.customers.table.name": "\"<schemaname>\".\"INVENTORY_CUSTOMERS\""
    }
}

```

We deploy the connectors by posting the connector configuration json files to the Kafka Connect's API.

```
$ curl -X POST -H "Accept:application/json" -H  "Content-Type:application/json" http://localhost:8083/connectors/ -d @connect-postgres-source-8.json
{"name":"inventory-postgres-source","config":{"connector.class":"io.debezium.connector.postgresql.PostgresConnector","tasks.max":"1","database.hostname":"postgres","database.port":"5432","database.user":"${file:/kafka/custom-config/secrets.properties:connection2-user}","database.password":"${file:/kafka/custom-config/secrets.properties:connection2-password}","database.dbname":"postgres","database.server.name":"dbserver2","schema.include.list":"inventory","name":"inventory-mysql-source"},"tasks":[],"type":"source"}
$
$ curl -X POST -H "Accept:application/json" -H "Content-Type:application/json" -d @connect-hana-sink-8.json http://localhost:8083/connectors/
{"name":"inventory8-hana-sink","config":{"connector.class":"com.sap.kafka.connect.sink.hana.HANASinkConnector","tasks.max":"1","topics":"dbserver2.inventory.customers","connection.url":"jdbc:sap://...
$
$ curl http://localhost:8083/connectors/
["inventory-postgres-source","inventory8-hana-sink"]
$
```

The above result shows that the connectors are successfully deployed.

##### Step 5: Interactively update the MySQL Table and verify the result in HANA Table

After starting the connectors, you will find table `inventory8_customers` in HANA. The replicated HANA table has the additional column `__deleted`. When the connector is configured with `delete.enabled=false`, the deleted record will be kept but the value of this column will be set to true. In contrast, when the connector is configured with `delete.enabled=true`, the deleted record will be deleted.

```
select * from inventory8_customers;
  id  first_name  last_name  email                  __deleted
----  ----------  ---------  ---------------------  ---------
1001  Sally       Thomas     sally.thomas@acme.com  false    
1002  George      Bailey     gbailey@foobar.com     false    
1003  Edward      Walker     ed@walker.com          false    
1004  Anne        Kretchmar  annek@noanswer.org     false    
```

At Postgres Command Line Client,  run the following SQL to insert a new record.
```
postgres=# insert into customers values (default, 'Sarah', 'Thompson', 'kitt@acme.com');
INSERT 0 1
postgres=# select * from customers;
  id  | first_name | last_name |         email         
------+------------+-----------+-----------------------
 1001 | Sally      | Thomas    | sally.thomas@acme.com
 1002 | George     | Bailey    | gbailey@foobar.com
 1003 | Edward     | Walker    | ed@walker.com
 1004 | Anne       | Kretchmar | annek@noanswer.org
 1005 | Sarah      | Thompson  | kitt@acme.com
(5 rows)
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

At Postgres Command Line Client,  run the following SQL to update one record.

```
postgres=# update customers set first_name='Anne Marie' where id=1004;
UPDATE 1
postgres=# select * from customers;
  id  | first_name | last_name |         email         
------+------------+-----------+-----------------------
 1001 | Sally      | Thomas    | sally.thomas@acme.com
 1002 | George     | Bailey    | gbailey@foobar.com
 1003 | Edward     | Walker    | ed@walker.com
 1005 | Sarah      | Thompson  | kitt@acme.com
 1004 | Anne Marie | Kretchmar | annek@noanswer.org
(5 rows)
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
postgres=# delete from customers where id=1005;
DELETE 1
postgres=# select * from customers;
  id  | first_name | last_name |         email         
------+------------+-----------+-----------------------
 1001 | Sally      | Thomas    | sally.thomas@acme.com
 1002 | George     | Bailey    | gbailey@foobar.com
 1003 | Edward     | Walker    | ed@walker.com
 1004 | Anne Marie | Kretchmar | annek@noanswer.org
(4 rows)
```

At HANA, verify the updated table.

As `deleted.enabled` is set to false, the record is only marked as `deleted` but not deleted.
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

##### Step 6: Shut down

Use `docker-compose down` to shutdown the containers.

```
$ docker-compose down                                                                                           
Stopping inventory8db_connect_1   ... done
Stopping inventory8db_kafka_1     ... done
Stopping inventory8db_postgres_1  ... done
Stopping inventory8db_zookeeper_1 ... done
Removing inventory8db_connect_1   ... done
Removing inventory8db_kafka_1     ... done
Removing inventory8db_postgres_1  ... done
Removing inventory8db_zookeeper_1 ... done
Removing network inventory8db_default
$ 
```
