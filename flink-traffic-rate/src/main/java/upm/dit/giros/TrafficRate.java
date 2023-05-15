package upm.dit.giros;

import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.functions.windowing.ProcessAllWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.GlobalWindow;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.apache.flink.util.Collector;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.Iterator;
import java.util.Properties;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

public class TrafficRate {

	public static void main(String[] args) throws Exception {
		Properties props = new Properties();
		props.put("bootstrap.servers", args[0]);
		props.put("group.id", "traffic-rate-group");
		props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
		props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
		
		// set up the streaming execution environment
		final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

		// Consume data stream from the Kafka input topic
        FlinkKafkaConsumer<String> consumer = new FlinkKafkaConsumer<String>(args[1], new SimpleStringSchema(), props);
		consumer.setStartFromLatest();

		//Produce data stream on the Kafka output topic
		FlinkKafkaProducer<String> producer = new FlinkKafkaProducer<String>(args[2], new SimpleStringSchema(), props);


		DataStreamSource<String> dss  = env.addSource((SourceFunction<String>) consumer);	

		DataStream<String> metric_values = dss.map(new MapFunction<String, String>(){
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
					JSONObject json_obj = new JSONObject(value);
					JSONObject data = new JSONObject(json_obj.get("data").toString());
					JSONArray result = new JSONArray(data.get("result").toString());
					JSONObject metric = result.getJSONObject(0);
					JSONArray metric_data = new JSONArray(metric.get("value").toString());
					value = metric_data.get(1).toString();
					*/
					
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return value;
		    }
		});

		metric_values.countWindowAll(2,1).process(new EventsAggregation()).addSink((SinkFunction<String>)producer);
		
		// execute program
		env.execute("Traffic Rate");
	}

	private static String aggregationMetrics(String value1, String value2){
		String traffic_rate = Float.toString(Float.parseFloat(value2) -  Float.parseFloat(value1));
		String value = new String();
		try {
			JSONObject result = new JSONObject();
			result.accumulate("metric_name", "TafficRate");
			result.accumulate("value", traffic_rate);
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

	public static class EventsAggregation extends ProcessAllWindowFunction<String, String, GlobalWindow>{

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
			if(windowSize == 2){
				Iterator<String> it = elements.iterator();
				String new_metric = aggregationMetrics(it.next(), it.next());
				out.collect(new_metric);
			}
		}
	} 
}
