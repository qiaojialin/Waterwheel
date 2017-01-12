package indexingTopology.bolt;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import indexingTopology.DataSchema;
import javafx.util.Pair;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import indexingTopology.cache.*;
import indexingTopology.config.TopologyConfig;
import indexingTopology.filesystem.FileSystemHandler;
import indexingTopology.filesystem.HdfsFileSystemHandler;
import indexingTopology.filesystem.LocalFileSystemHandler;
import indexingTopology.streams.Streams;
import indexingTopology.util.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by acelzj on 11/15/16.
 */
public class RangeQueryChunkScannerBolt extends BaseRichBolt{

    OutputCollector collector;

    private int bTreeOder;

    private transient LRUCache<CacheMappingKey, CacheUnit> cacheMapping;

    private transient Kryo kryo;

    private DataSchema schema;

    Long startTime;

    Long timeCostOfReadFile;

    Long timeCostOfSearching;

    Long timeCostOfDeserializationATree;

    Long timeCostOfDeserializationALeaf;


    public RangeQueryChunkScannerBolt(DataSchema schema) {
        this.schema = schema;
    }

    public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
        collector = outputCollector;
        bTreeOder = 4;
        cacheMapping = new LRUCache<CacheMappingKey, CacheUnit>(TopologyConfig.CACHE_SIZE);
        kryo = new Kryo();
        kryo.register(BTree.class, new KryoTemplateSerializer());
        kryo.register(BTreeLeafNode.class, new KryoLeafNodeSerializer());
    }

    public void execute(Tuple tuple) {

        RangeQuerySubQuery subQuery = (RangeQuerySubQuery) tuple.getValue(0);

        Long queryId = subQuery.getQueryId();
        Double leftKey = subQuery.getlefKey();
        Double rightKey = subQuery.getRightKey();
        String fileName = subQuery.getFileName();
        Long timestampLowerBound = subQuery.getStartTimestamp();
        Long timestampUpperBound = subQuery.getEndTimestamp();

        System.out.println("file name " + fileName);

        RandomAccessFile file = null;

        FileScanMetrics metrics = new FileScanMetrics();

        Long startTime = System.currentTimeMillis();

        timeCostOfReadFile = ((long) 0);

        timeCostOfSearching = ((long) 0);

        timeCostOfDeserializationATree = ((long) 0);

        timeCostOfDeserializationALeaf = ((long) 0);

        System.out.println("left key " + leftKey);

        System.out.println("right key " + rightKey);


        /*
        try {
            FileSystemHandler fileSystemHandler = null;
            if (TopologyConfig.HDFSFlag) {
                fileSystemHandler = new HdfsFileSystemHandler(TopologyConfig.dataDir);
            } else {
                fileSystemHandler = new LocalFileSystemHandler(TopologyConfig.dataDir);

            }

            CacheMappingKey mappingKey = new CacheMappingKey(fileName, 0);
            Pair data = (Pair) getFromCache(mappingKey);


            if (data == null) {
                data = getTemplateFromExternalStorage(fileSystemHandler, fileName);



                CacheData cacheData = new TemplateCacheData(data);

                putCacheData(cacheData, mappingKey);
            }

            BTree deserializedTree = (BTree) data.getKey();

            Integer length = (Integer) data.getValue();


            BTreeNode mostLeftNode = deserializedTree.findLeafNodeShouldContainKeyInDeserializedTemplate(leftKey);
            BTreeNode mostRightNode = deserializedTree.findLeafNodeShouldContainKeyInDeserializedTemplate(rightKey);

            Long searchStartTime = System.currentTimeMillis();
            List<Integer> offsets = deserializedTree.getOffsetsOfLeaveNodesShouldContainKeys(mostLeftNode
                        , mostRightNode);

            timeCostOfSearching += (System.currentTimeMillis() - searchStartTime);

            BTreeLeafNode leaf;

            for (Integer offset : offsets) {
                mappingKey = new CacheMappingKey(fileName, offset + length + 4);
                leaf = (BTreeLeafNode) getFromCache(mappingKey);
                if (leaf == null) {
//                    leaf = getLeafFromExternalStorage(fileSystemHandler, fileName, offset + length + 4);
                    leaf = getLeafFromExternalStorage(fileName, offset + length + 4);
                } else {
                    CacheData cacheData = new LeafNodeCacheData(leaf);
                    putCacheData(cacheData, mappingKey);
                }
                searchStartTime = System.currentTimeMillis();

                ArrayList<byte[]> tuples = getTuplesWithinTimeStamp(leaf, timestampLowerBound,
                        timestampUpperBound);

//                ArrayList<byte[]> tuples = leaf.rangeSearchAndGetTuples(timestampLowerBound, timestampUpperBound);
                timeCostOfSearching += (System.currentTimeMillis() - searchStartTime);
                if (tuples.size() != 0) {
                    serializedTuples.addAll(tuples);
                }
            }
            metrics.setTotalTime(System.currentTimeMillis() - startTime);
            metrics.setFileReadingTime(timeCostOfReadFile);
            metrics.setLeafDeserializationTime(timeCostOfDeserializationALeaf);
            metrics.setTreeDeserializationTime(timeCostOfDeserializationATree);
            metrics.setSearchTime(timeCostOfSearching);
            collector.emit(Streams.FileSystemQueryStream, new Values(queryId, serializedTuples, metrics));

            collector.emit(Streams.FileSubQueryFinishStream, new Values("finished"));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            */
        if (leftKey.compareTo(rightKey) == 0) {
            try {
                executePointQuery(queryId, leftKey, fileName, timestampLowerBound, timestampUpperBound);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        } else {
            try {
                executeRangeQuery(queryId, leftKey, rightKey, fileName, timestampLowerBound, timestampUpperBound);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
//        outputFieldsDeclarer.declareStream(NormalDistributionIndexingTopology.FileSystemQueryStream,
//                new Fields("leftKey", "rightKey", "serializedTuples"));
//        outputFieldsDeclarer.declareStream(NormalDistributionIndexingAndRangeQueryTopology.FileSystemQueryStream,
//                new Fields("queryId", "serializedTuples"));

//        outputFieldsDeclarer.declareStream(NormalDistributionIndexingTopology.FileSystemQueryStream,
//                new Fields("queryId", "serializedTuples", "timeCostOfReadFile", "timeCostOfDeserializationALeaf",
//                        "timeCostOfDeserializationATree"));

        outputFieldsDeclarer.declareStream(Streams.FileSystemQueryStream,
                new Fields("queryId", "serializedTuples", "metrics"));

        outputFieldsDeclarer.declareStream(Streams.FileSubQueryFinishStream,
                new Fields("finished"));

    }

    private void executeRangeQuery(Long queryId, Double leftKey, Double rightKey, String fileName, Long timestampLowerBound, Long timestampUpperBound) throws IOException {

        System.out.println("range query");

        FileScanMetrics metrics = new FileScanMetrics();

        ArrayList<byte[]> serializedTuples = new ArrayList<byte[]>();

        Pair data = getTreeData(fileName);

        BTree deserializedTree = (BTree) data.getKey();

        Integer length = (Integer) data.getValue();

        BTreeNode mostLeftNode = deserializedTree.findLeafNodeShouldContainKeyInDeserializedTemplate(leftKey);

        BTreeNode mostRightNode = deserializedTree.findLeafNodeShouldContainKeyInDeserializedTemplate(rightKey);

        List<Integer> offsets = deserializedTree.getOffsetsOfLeaveNodesShouldContainKeys(mostLeftNode, mostRightNode);

        BTreeLeafNode leaf;

        for (Integer offset : offsets) {
            CacheMappingKey mappingKey = new CacheMappingKey(fileName, offset + length + 4);
            leaf = (BTreeLeafNode) getFromCache(mappingKey);
            if (leaf == null) {
                leaf = getLeafFromExternalStorage(fileName, offset + length + 4);
            } else {
                CacheData cacheData = new LeafNodeCacheData(leaf);
                putCacheData(cacheData, mappingKey);
            }

            ArrayList<byte[]> allTuples = leaf.getTuples(leftKey, rightKey);

            ArrayList<byte[]> tuples = getTuplesWithinTimeStamp(allTuples, timestampLowerBound, timestampUpperBound);

            if (tuples.size() != 0) {
                serializedTuples.addAll(tuples);
            }
        }

        collector.emit(Streams.FileSystemQueryStream, new Values(queryId, serializedTuples, metrics));

        collector.emit(Streams.FileSubQueryFinishStream, new Values("finished"));

    }

    private void executePointQuery(Long queryId, Double key, String fileName, Long timestampLowerBound, Long timestampUpperBound) throws IOException {

        System.out.println("point query");

        FileScanMetrics metrics = new FileScanMetrics();

        Pair data = getTreeData(fileName);

        BTree deserializedTree = (BTree) data.getKey();

        Integer length = (Integer) data.getValue();

        int offset = deserializedTree.getOffsetOfLeaveNodeShouldContainKey(key);

        offset += (length + 4);

        BTreeLeafNode leaf;

        CacheMappingKey mappingKey = new CacheMappingKey(fileName, offset);

        leaf = (BTreeLeafNode) getFromCache(mappingKey);

        if (leaf == null) {
            leaf = getLeafFromExternalStorage(fileName, offset);
        } else {
            CacheData cacheData = new LeafNodeCacheData(leaf);
            putCacheData(cacheData, mappingKey);
        }

        ArrayList<byte[]> tuples = leaf.searchAndGetTuples(key);

        ArrayList<byte[]> serializedTuples = getTuplesWithinTimeStamp(tuples, timestampLowerBound, timestampUpperBound);

        collector.emit(Streams.FileSystemQueryStream,
                new Values(queryId, serializedTuples, metrics));

        collector.emit(Streams.FileSubQueryFinishStream,
                new Values("finished"));
    }

    private Object getFromCache(CacheMappingKey mappingKey) {
        if (cacheMapping.get(mappingKey) == null) {
            return null;
        }
        return cacheMapping.get(mappingKey).getCacheData().getData();
    }


    private Pair getTemplateFromExternalStorage(FileSystemHandler fileSystemHandler, String fileName) {
        //        startTimeOfReadFile = System.currentTimeMillis();
        fileSystemHandler.openFile("/", fileName);
//        timeCostOfReadFile = System.currentTimeMillis() - startTimeOfReadFile;
        byte[] temlateLengthInBytes = new byte[4];
        fileSystemHandler.readBytesFromFile(temlateLengthInBytes);

        Input input = new Input(temlateLengthInBytes);
        int length = input.readInt();

//        byte[] serializedTree = new byte[TopologyConfig.TEMPLATE_SIZE];
        byte[] serializedTree = new byte[length];
//                DeserializationHelper deserializationHelper = new DeserializationHelper();
        BytesCounter counter = new BytesCounter();

//        startTimeOfReadFile = System.currentTimeMillis();
        fileSystemHandler.readBytesFromFile(0, serializedTree);
//        timeCostOfReadFile += (System.currentTimeMillis() - startTimeOfReadFile);

//        Long startTimeOfDeserializationATree = System.currentTimeMillis();
//        BTree deserializedTree = DeserializationHelper.deserializeBTree(serializedTree, bTreeOder, counter);
        input = new Input(serializedTree);
        BTree deserializedTree = kryo.readObject(input, BTree.class);
//        timeCostOfDeserializationATree = System.currentTimeMillis() - startTimeOfDeserializationATree;
//        startTimeOfReadFile = System.currentTimeMillis();
        fileSystemHandler.closeFile();
//        timeCostOfReadFile += (System.currentTimeMillis() - startTimeOfReadFile);

        return new Pair(deserializedTree, length);
    }


    private void putCacheData(CacheData cacheData, CacheMappingKey mappingKey) {
        CacheUnit cacheUnit = new CacheUnit();
        cacheUnit.setCacheData(cacheData);
        cacheMapping.put(mappingKey, cacheUnit);
    }


    private ArrayList<byte[]> getTuplesWithinTimeStamp(BTreeLeafNode leaf, Long timestampLowerBound, Long timestampUpperBound, Double leftKey, Double rightKey)
            throws IOException {

        ArrayList<byte[]> serializedTuples = new ArrayList<>();

        List<byte[]> tuples = leaf.getTuples(leftKey, rightKey);

        for (int i = 0; i < tuples.size(); ++i) {
            Values deserializedTuple = DeserializationHelper.deserialize(tuples.get(i));
            if (timestampLowerBound <= (Long) deserializedTuple.get(3) &&
                    timestampUpperBound >= (Long) deserializedTuple.get(3)) {
                serializedTuples.add(tuples.get(i));
            }
        }

        return serializedTuples;
    }

    private ArrayList<byte[]> getTuplesWithinTimeStamp(ArrayList<byte[]> tuples, Long timestampLowerBound, Long timestampUpperBound)
            throws IOException {

        ArrayList<byte[]> serializedTuples = new ArrayList<>();

        for (int i = 0; i < tuples.size(); ++i) {
            Values deserializedTuple = DeserializationHelper.deserialize(tuples.get(i));
            if (timestampLowerBound <= (Long) deserializedTuple.get(schema.getNumberOfFileds()) &&
                    timestampUpperBound >= (Long) deserializedTuple.get(schema.getNumberOfFileds())) {
                serializedTuples.add(tuples.get(i));
            }
        }

        return serializedTuples;
    }

    /*
    private BTreeLeafNode getLeafFromExternalStorage(FileSystemHandler fileSystemHandler, String fileName, int offset)
            throws IOException {
        byte[] lengthInByte = new byte[4];
        Long startTimeOfReadFile = System.currentTimeMillis();
        fileSystemHandler.openFile("/", fileName);
        timeCostOfReadFile = System.currentTimeMillis() - startTimeOfReadFile;

        startTimeOfReadFile = System.currentTimeMillis();
        fileSystemHandler.seek(offset);
        timeCostOfReadFile += (System.currentTimeMillis() - startTimeOfReadFile);

        startTimeOfReadFile = System.currentTimeMillis();
        fileSystemHandler.readBytesFromFile(offset, lengthInByte);
        timeCostOfReadFile += (System.currentTimeMillis() - startTimeOfReadFile);

        int lengthOfLeaveInBytes = ByteBuffer.wrap(lengthInByte, 0, 4).getInt();
        byte[] leafInByte = new byte[lengthOfLeaveInBytes+1];

        startTimeOfReadFile = System.currentTimeMillis();
        fileSystemHandler.seek(offset + 4);
        timeCostOfReadFile += (System.currentTimeMillis() - startTimeOfReadFile);


        startTimeOfReadFile = System.currentTimeMillis();
        fileSystemHandler.readBytesFromFile(offset + 4, leafInByte);
        timeCostOfReadFile += (System.currentTimeMillis() - startTimeOfReadFile);

        Long startTimeOfDeserializationALeaf = System.currentTimeMillis();
//        BytesCounter counter = new BytesCounter();
//        BTreeLeafNode leaf = DeserializationHelper.deserializeLeaf(leafInByte, bTreeOder, counter);
        Input input = new Input(leafInByte);
        BTreeLeafNode leaf = kryo.readObject(input, BTreeLeafNode.class);
        timeCostOfDeserializationALeaf += (System.currentTimeMillis() - startTimeOfDeserializationALeaf);

        fileSystemHandler.closeFile();
        return leaf;

    }
    */

    private BTreeLeafNode getLeafFromExternalStorage(String fileName, int offset)
            throws IOException {

        FileSystemHandler fileSystemHandler = null;
        if (TopologyConfig.HDFSFlag) {
            fileSystemHandler = new HdfsFileSystemHandler(TopologyConfig.dataDir);
        } else {
            fileSystemHandler = new LocalFileSystemHandler(TopologyConfig.dataDir);
        }

        byte[] lengthInByte = new byte[4];
        fileSystemHandler.openFile("/", fileName);

        fileSystemHandler.seek(offset);

        fileSystemHandler.readBytesFromFile(offset, lengthInByte);

        int lengthOfLeaveInBytes = ByteBuffer.wrap(lengthInByte, 0, 4).getInt();
        byte[] leafInByte = new byte[lengthOfLeaveInBytes+1];

        fileSystemHandler.seek(offset + 4);

        fileSystemHandler.readBytesFromFile(offset + 4, leafInByte);

        Input input = new Input(leafInByte);
        BTreeLeafNode leaf = kryo.readObject(input, BTreeLeafNode.class);

        fileSystemHandler.closeFile();
        return leaf;
    }


    private Pair getTreeData(String fileName) {
        Pair data = null;
        try {
            FileSystemHandler fileSystemHandler = null;
            if (TopologyConfig.HDFSFlag) {
                fileSystemHandler = new HdfsFileSystemHandler(TopologyConfig.dataDir);
            } else {
                fileSystemHandler = new LocalFileSystemHandler(TopologyConfig.dataDir);
            }

            CacheMappingKey mappingKey = new CacheMappingKey(fileName, 0);

            data = (Pair) getFromCache(mappingKey);

            if (data == null) {
                data = getTemplateFromExternalStorage(fileSystemHandler, fileName);

                CacheData cacheData = new TemplateCacheData(data);

                putCacheData(cacheData, mappingKey);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return data;
    }
}