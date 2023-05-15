# Apache Flink Streaming Aggregation Application

Simple [Apache Flink](https://flink.apache.org/) streaming application to perform aggregations from [Prometheus](https://prometheus.io/docs/introduction/overview/) metrics.

## Table of Contents

- [Requirements](#requirements)
- [Deploy the testbed scenario](#deploy-the-testbed-scenario)
- [Build the Java project and use the Apache Flink REST API to run the application](#build-the-java-project-and-use-the-apache-flink-rest-api-to-run-the-application)
- [Tester for Apache Flink applications](#tester-for-apache-flink-applications)
  - [Kafka service name resolution on localhost](#kafka-service-name-resolution-on-localhost)
  - [Running and debugging Java applications with input arguments on Visual Studio Code](#running-and-debugging-java-applications-with-input-arguments-on-visual-studio-code)
- [Documentation](#documentation)
- [License](#license)

## Requirements

- Docker (_Tested with version 20.10.14_)
- Docker-compose (_Tested with version 1.27.4_)

## Deploy the testbed scenario

To deploy the docker-compose scenario, execute the following command:
```bash
$ docker-compose up
```

In case you are interested in running the scenario in background, use the following command:
```bash
$ docker-compose up -d
```

Tear the scenario down as follows:
```bash
$ docker-compose down
```

> **Note:**
>
> In the `.env` file, environment variables that take the microservices defined in the docker-compose are defined and parameterized (e.g., to define the user and password of the Apache NiFi service or the topics of the Apache Kafka service).

## Build the Java project and use the Apache Flink REST API to run the application

1. Build and compile the Java project:
```bash
$ cd flink-traffic-rate/
$ mvn clean install
```

2. Upload the JAR to the Flink cluster:

```bash
$ curl -X POST -H "Expect:" -F "jarfile=@flink-traffic-rate/target/traffic-rate-1.0.jar" http://localhost:8084/jars/upload
```
> **Example result:**
> 
> ```bash
> {"filename":"/tmp/flink-web-01f2115a-8584-486d-ab52-b9c59c598cb3/flink-web-upload/3da912fb-c7d4-4875-998e-b711d6f0e1d6_traffic-rate-1.0.jar","status":"success"}
> ```

2. Return a list of all JARs previously uploaded via '/jars/upload' (find the JAR ID of the Flink application):

```bash
$ curl -X GET http://localhost:8084/jars
```
> **Example result:**
> 
> ```bash
> {"address":"http://flink-jobmanager:8081","files":[{"id":"3da912fb-c7d4-4875-998e-b711d6f0e1d6_traffic-rate-1.0.jar","name":"traffic-rate-1.0.jar","uploaded":1684142167012,"entry":[{"name":"upm.dit.giros.TrafficRate","description":null}]}]}
> ```

3. Submit a Flink job by running a JAR previously uploaded via '/jars/upload'. Program arguments can be passed both via the JSON request (recommended) or query parameters.

- **jarid**: String value that identifies a jar. When uploading the jar a path is returned, where the filename is the ID. This value is equivalent to the `id` field in the list of uploaded jars (/jars).

- **programArg**: Comma-separated list of program arguments (i.e., Kafka broker endpoint and input and output Kafka topics). 

- **entry-class** (optional): String value that specifies the fully qualified name of the entry point class. Overrides the class defined in the jar file manifest.

```bash
$ curl -X POST http://localhost:8084/jars/3da912fb-c7d4-4875-998e-b711d6f0e1d6_traffic-rate-1.0.jar/run?programArg="kafka:9092,input,output"
```
> **Example result:**
> 
> ```bash
> {"jobid":"796498e8febb1c378b63ecc0b8d5d878"}
> ```

4. Return an overview over all Flink jobs and their current state:

```bash
$ curl -X GET http://localhost:8084/jobs
```
> **Example result:**
> 
> ```bash
> {"jobs":[{"id":"796498e8febb1c378b63ecc0b8d5d878","status":"RUNNING"}]}
> ```

5. Cancel/Stop Flink job:

```bash
$ curl -X PATCH http://localhost:8084/jobs/dc6dcc99b55184a40e2c0390559a3b84?mode=cancel
```

6. Delete a JAR previously uploaded:

```bash
$ curl -X DELETE http://localhost:8084/jars/d18cd4e3-09cf-4556-8072-3a6409270b0d_netflow-driver-1.0.jar
```

## Tester for Apache Flink applications

For testing Apache Flink streaming applications is not needed to deploy a dedicated Flink cluster. Instead, the applications can be debugged directly on top of the localhost JVM.

This section provides guidelines to run a simple "tester" to be able to test these applications based on Apache Flink, incorporating the utility of deploying Kafka as a Docker service from which to read and write the input and output data of the application, as well as the possibility of passing input arguments for the applications from the programming IDE (in this specific case for [Visual Studio Code (VSC)](https://code.visualstudio.com/)).

### Kafka service name resolution on localhost

There is a [Kafka issue](https://stackoverflow.com/questions/35861501/kafka-in-docker-not-working) related to the Docker service name resolution from outside.  Applications reading or writing from Kafka running on localhost (e.g., Apache Flink applications) try to access the service using the name `kafka`. An easier solution is configuring the Kafka Docker service to advertise on `kafka` host name  (i.e., configure `KAFKA_ADVERTISED_HOST_NAME=kafka` environment variable in the Kafka Docker service definition) and, because it's exposed on the host machine on `localhost:9092`, adding an entry in `/etc/hosts` for `kafka` to resolve to localhost.

- Kafka Docker service definition on the docker-compose file:
```bash
  kafka:
    image: wurstmeister/kafka:latest
    hostname: kafka
    container_name: kafka
    ports:
      - "9092:9092"
    environment:
      KAFKA_ADVERTISED_HOST_NAME: kafka
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_PORT: 9092
      KAFKA_LOG_DIRS: "/tmp/kafka-logs"
      KAFKA_CREATE_TOPICS: "${KAFKA_INPUT_TOPIC},${KAFKA_OUTPUT_TOPIC}"
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock
    depends_on:
      - zookeeper
    logging:
      driver: none
```

- Kafka service name resolution in `/etc/hosts` file on localhost:
```bash
127.0.0.1	localhost kafka
```

By doing this Kafka can be accessed from both other Docker containers and from localhost.

In Windows10 the process will be the same, but the `hosts` file is normally in:
```
C:\Windows\System32\drivers\etc\hosts
```

### Running and debugging Java applications with input arguments on Visual Studio Code

The Visual Studio Code (VSC) IDE includes different options and settings to run and debug the code. For Java applications, it allows customizing particular [launch configuration options](https://code.visualstudio.com/docs/java/java-debugging#_configuration-options) easily with a JSON file template, such as to define arguments and environmental variables. To do that, from the application workspace location (i.e., [`./flink-traffic-rate`](flink-traffic-rate/)) you must select the `Add Configuration...` option on the `Run` section of the `VSC` toolbox. This action will create a `launch.json` file located in a `.vscode` folder in your workspace. 

The following sample `launch.json` file is an example for the Traffic Rate Apache Flink Java application:
```bash
{
    // Use IntelliSense para saber los atributos posibles.
    // Mantenga el puntero para ver las descripciones de los existentes atributos.
    // Para más información, visite: https://go.microsoft.com/fwlink/?linkid=830387
    "version": "0.2.0",
    "configurations": [
        {
            "type": "java",
            "name": "Current File",
            "request": "launch",
            "mainClass": "${file}"
        },
        {
            "type": "java",
            "name": "TrafficRate",
            "request": "launch",
            "mainClass": "upm.dit.giros.TrafficRate",
            "projectName": "traffic-rate"
        }
    ]
}
```

In the `configurations` field, you can include different extra configurations for your application. For example,  the `args` option allows specifying the command-line arguments passed to the program. The following  sample `launch.json` file includes the arguments needed to run the NetFlow Driver Java application:
```bash
{
    // Use IntelliSense para saber los atributos posibles.
    // Mantenga el puntero para ver las descripciones de los existentes atributos.
    // Para más información, visite: https://go.microsoft.com/fwlink/?linkid=830387
    "version": "0.2.0",
    "configurations": [
        {
            "type": "java",
            "name": "Current File",
            "request": "launch",
            "mainClass": "${file}"
        },
        {
            "type": "java",
            "name": "TrafficRate",
            "request": "launch",
            "mainClass": "upm.dit.giros.TrafficRate",
            "projectName": "traffic-rate",
            "args": [
                "kafka:9092",
                "input",
                "output"
            ]
        }
    ]
}
```

The resulting configuration file is available [here](flink-traffic-rate/.vscode/launch.json).

## Documentation

- [Apache Flink Documentation](https://flink.apache.org/)
- [Apache Flink REST API](https://nightlies.apache.org/flink/flink-docs-release-1.14/docs/ops/rest_api/)
- [Prometheus Documentation](https://prometheus.io/docs/introduction/overview/)
- [Apache NiFi Documentation](https://nifi.apache.org/)

## License

This project is licensed under [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0).