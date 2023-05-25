package upm.dit.giros;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.streaming.api.functions.windowing.ProcessAllWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.GlobalWindow;
import org.apache.flink.util.Collector;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.Iterator;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

public class TrafficRate {

	public static void main(String[] args) throws Exception {

		// Set up the streaming execution environment
		final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

		// KAFKA CONSUMER
		KafkaSource<String> consumer = KafkaSource.<String>builder()
				.setTopics(args[1])
				.setGroupId("traffic-rate-group")
				.setBootstrapServers(args[0])
				.setStartingOffsets(OffsetsInitializer.latest())
				.setValueOnlyDeserializer((DeserializationSchema<String>) new SimpleStringSchema())
				.build();

		// KAFKA PRODUCER
		KafkaSink<String> producer = KafkaSink.<String>builder()
				.setBootstrapServers(args[0])
				.setRecordSerializer(KafkaRecordSerializationSchema.builder()
						.setTopic(args[2])
						.setValueSerializationSchema((SerializationSchema<String>) new SimpleStringSchema())
						.build())
				.setDeliverGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
				.build();


		DataStream<String> dss = env.fromSource(consumer, WatermarkStrategy.noWatermarks(), "Kafka Source");

		DataStream<String> metric_values = dss.map(new MapFunction<String, String>() {
			@Override
			public String map(String value) throws Exception {
				try {
					System.out.println("\nPrometheus metric raw value: " + value);

					// SI HAY TRANSFORMACIONES DE DATOS PREVIA CON APACHE NIFI:
					JSONArray json_array = new JSONArray(value);
					JSONObject metric = json_array.getJSONObject(0);
					JSONArray metric_data = new JSONArray(metric.get("value").toString());
					value = metric_data.get(1).toString();

					// SI NO HAY TRANSFORMACIONES DE DATOS PREVIA CON APACHE NIFI:
					/*
					 * JSONObject json_obj = new JSONObject(value);
					 * JSONObject data = new JSONObject(json_obj.get("data").toString());
					 * JSONArray result = new JSONArray(data.get("result").toString());
					 * JSONObject metric = result.getJSONObject(0);
					 * JSONArray metric_data = new JSONArray(metric.get("value").toString());
					 * value = metric_data.get(1).toString();
					 */

				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return value;
			}
		});

		metric_values.countWindowAll(2, 1).process(new EventsAggregation(args[3])).sinkTo(producer);

		// execute program
		env.execute("Traffic Rate");
	}

	private static String aggregationMetrics(String value1, String value2, String duration) {
		String traffic_rate = Double.toString((Double.parseDouble(value2) - Double.parseDouble(value1))*8/Double.parseDouble(duration));
		String value = new String();
		try {
			JSONObject result = new JSONObject();
			result.accumulate("metric_name", "TafficRate");
			result.accumulate("value", traffic_rate);
			result.accumulate("unit", "bps");
			Timestamp timestamp = new Timestamp(System.currentTimeMillis());
			result.accumulate("timestamp", timestamp.toString());
			value = result.toString();
			System.out.println("\nPrometheus metric enriched value: " + value);
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return value;
	}

	public static class EventsAggregation extends ProcessAllWindowFunction<String, String, GlobalWindow> {
		
		private String duration;

		EventsAggregation(String duration) {
			this.duration = duration;
		}

		private int size(Iterable data) {

			if (data instanceof Collection) {
				return ((Collection<?>) data).size();
			}
			int counter = 0;
			for (Object i : data) {
				counter++;
			}
			return counter;
		}

		@Override
		public void process(Context context, Iterable<String> elements, Collector<String> out) throws Exception {
			int windowSize = size(elements);
			if (windowSize == 2) {
				Iterator<String> it = elements.iterator();
				String new_metric = aggregationMetrics(it.next(), it.next(), duration);
				out.collect(new_metric);
			}
		}
	}
	
}
