package indexingTopology.common.data;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.JSONSerializer;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.junit.Test;

import java.io.FileNotFoundException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

/**
 * Created by robert on 8/2/17.
 */
public class DataSchemaTest {



    @Test
    public void SchemaTest() {
        DataSchema schema = new DataSchema();
        schema.addDoubleField("double");
        schema.addIntField("int");
        schema.setPrimaryIndexField("double");
        assertEquals(schema.getNumberOfFields(), 2);
    }

    @Test
    public void SerializationTest() {
        DataSchema schema = new DataSchema();
        schema.addDoubleField("f1");
        schema.addLongField("f2");
        schema.addDoubleField("f3");
        schema.addVarcharField("f4",64);
        schema.addVarcharField("f5",64);
        schema.addDoubleField("f6");
        schema.addVarcharField("f7",64);
        DataTuple dataTuple = new DataTuple(0.01, 10L);
        dataTuple.add(null);
        dataTuple.add("");
        dataTuple.add("a");
        dataTuple.add(111.1);
        dataTuple.add("");
        System.out.println(dataTuple);
        byte[] bytes = schema.serializeTuple(dataTuple);
        DataTuple dataTupleDeserialized = schema.deserializeToDataTuple(bytes);
        assertEquals(0.01, dataTupleDeserialized.get(0));
        assertEquals(10L, dataTupleDeserialized.get(1));
        assertEquals(null, dataTupleDeserialized.get(2));
        assertEquals("", dataTupleDeserialized.get(3));
        assertEquals("a", dataTupleDeserialized.get(4));
        assertEquals(111.1, dataTupleDeserialized.get(5));
        assertEquals("", dataTupleDeserialized.get(6));
        Date dateOld = new Date(System.currentTimeMillis()); // 根据long类型的毫秒数生命一个date类型的时间
        System.out.println(dateOld);
        String dateValue = "2018-01-23 16:01:01";
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date date = null;
        try {
            date = dateFormat.parse(dateValue);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        System.out.println("hehe");
    }

    @Test
    public void SerializationTest1() {
        DataSchema schema = new DataSchema();
        schema.addDoubleField("f1");
        schema.addLongField("f2");
        schema.addVarcharField("f3",3);
        schema.addVarcharField("f4", 2);
        DataTuple dataTuple = new DataTuple(0.01, 10L, "aaaaa", "bbbbbb");
        byte[] bytes = schema.serializeTuple(dataTuple);
        DataTuple dataTupleDeserialized = schema.deserializeToDataTuple(bytes);
        assertEquals(0.01, dataTupleDeserialized.get(0));
        assertEquals(10L, dataTupleDeserialized.get(1));
        assertEquals("aaaaa", dataTupleDeserialized.get(2));
        assertEquals("bbbbbb", dataTupleDeserialized.get(3));
    }

    @Test
    public void IndexFieldTest() {
        DataSchema schema = new DataSchema();
        schema.addDoubleField("f1");
        schema.addLongField("f2");
        schema.setPrimaryIndexField("f2");
        assertEquals("f2", schema.getIndexField());
        assertTrue(schema.getIndexType().type.equals(Long.class));

        DataTuple dataTuple = new DataTuple(0.01, 10L);

        assertEquals(0.01, schema.getValue("f1", dataTuple));

        assertEquals(10L, schema.getIndexValue(dataTuple));
    }

    @Test
    public void getTupleLength() {
        DataSchema schema = new DataSchema();
        schema.addDoubleField("f1");
        schema.addLongField("f2");
        schema.setPrimaryIndexField("f2");

        assertEquals( 16, schema.getTupleLength());
        assertEquals(8, schema.getIndexType().length);
    }

    @Test
    public void getFiledNames() {
        DataSchema schema = new DataSchema();
        schema.addDoubleField("f1");
        schema.addLongField("f2");
        assertEquals("[f1, f2]", schema.getFieldNames().toString());
    }

    @Test
    public void getTuplesFromJsonTest() throws FileNotFoundException, ParseException{
        DataSchema schema = new DataSchema();
        schema.addVarcharField("2" ,3);
        schema.addVarcharField("1", 3);
        schema.addVarcharField("date", 15);
        String jsonStr = "{\"result\":[{\"\":\"\",\"1\":null,\"date\":\"2017-12-01 10:40:00\"},{\"2\":\"efg\",\"1\":null,\"date\":\"2017-12-01 10:40:00\"}]}";
        JSONObject jsonObject = JSONObject.parseObject(jsonStr);
        JSONArray array = jsonObject.getJSONArray("result");
        List<DataTuple> list =  schema.getTuplesFromJsonArray(array);
        assertEquals(2, list.size());
    }

    @Test
    public void getTupleFromJsonAndJsonFromTupleTest() throws FileNotFoundException {
        DataSchema schema = new DataSchema();
        schema.addVarcharField("a2" ,3);
        schema.addVarcharField("a1", 3);
        schema.addVarcharField("date", 15);
        String jsonStr = "{\"a1\":null,\"date\":\"2017-12-01 10:40:00\"}";
        JSONObject jsonObject = JSONObject.parseObject(jsonStr);
        try{
            DataTuple tuple = schema.getTupleFromJsonObject(null);
            assertEquals(null,tuple);
            tuple = schema.getTupleFromJsonObject(jsonObject);
            jsonObject = schema.getJsonFromDataTuple(tuple);
            assertEquals(null, jsonObject.get("a2"));
            assertEquals(null, jsonObject.get("a1"));
            assertEquals("2017-12-01 10:40:00", jsonObject.get("date"));
        }catch (ParseException e){
            e.printStackTrace();
        }
    }

}
