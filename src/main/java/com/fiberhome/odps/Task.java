package com.fiberhome.odps;

import com.aliyun.odps.*;
import com.aliyun.odps.account.Account;
import com.aliyun.odps.account.AliyunAccount;
import com.aliyun.odps.data.Record;
import com.aliyun.odps.data.TableInfo;
import com.aliyun.odps.mapred.JobClient;
import com.aliyun.odps.mapred.MapperBase;
import com.aliyun.odps.mapred.RunningJob;
import com.aliyun.odps.mapred.bridge.WritableRecord;
import com.aliyun.odps.mapred.conf.JobConf;
import com.aliyun.odps.mapred.conf.SessionState;
import com.aliyun.odps.mapred.utils.InputUtils;
import com.aliyun.odps.mapred.utils.OutputUtils;
import com.aliyun.odps.tunnel.TableTunnel;
import com.aliyun.odps.tunnel.io.TunnelBufferedWriter;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

public class Task {
    private static final Logger LOG = LoggerFactory.getLogger(Task.class);

    private static Properties prop ;
    private static Odps odps;
    static {
        prop = new Properties();
        try {
            prop.load(Task.class.getClassLoader().getResourceAsStream("odps-core.properties"));
        } catch (IOException e) {
            throw new RuntimeException("", e);
        }
        odps = getOdps();
    }

    public static void main(String[] args) throws OdpsException, IOException {
        Task task = new Task();
        task.uploadData();
        task.execute();
        System.exit(0);
    }

    private void uploadData() throws OdpsException, IOException {
        LOG.info("----------------------------------------------------开始上传数据");
        String inTableName = getInputTableName();
        if(odps.tables().exists(inTableName)){
            odps.tables().delete(inTableName);
        }
        String outTableName = getOutTableName();
        if(odps.tables().exists(outTableName)){
            odps.tables().delete(outTableName);
        }
        TableSchema inSchema = new TableSchema();
        inSchema.addColumn(new Column("name",OdpsType.STRING));
        inSchema.addColumn(new Column("age",OdpsType.STRING));
        inSchema.addColumn(new Column("addr",OdpsType.STRING));

        ArrayList<Column> parArr = new ArrayList<Column>();
        Column parCol = new Column(getPartitionName(),OdpsType.STRING);
        parArr.add(parCol);
        inSchema.setPartitionColumns(parArr);
        odps.tables().create(inTableName,inSchema);

        PartitionSpec partitionSpec = new PartitionSpec();
        partitionSpec.set(getPartitionName(),getPartitionName());
        odps.tables().get(inTableName).createPartition(partitionSpec);
        TableTunnel tunnel = new TableTunnel(odps);
        TableTunnel.UploadSession uploadSession = tunnel.createUploadSession(odps.getDefaultProject(),inTableName,partitionSpec);
        TunnelBufferedWriter tunnelBufferedWriter = (TunnelBufferedWriter) uploadSession.openBufferedWriter();
        tunnelBufferedWriter.setBufferSize(20* 1024 * 1024);


        String dataPath = prop.getProperty("datapath") + File.separator + getInputTableName();
        try(BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(dataPath)))){
            String temp;
            while (null != (temp=br.readLine())){
                String[] arr = temp.split("\t",-1);
                if(arr.length==3){
                    Column[] columns = new Column[arr.length];
                    columns[0]=new Column("name",OdpsType.STRING,"name");
                    columns[1]=new Column("age",OdpsType.STRING,"age");
                    columns[2]=new Column("addr",OdpsType.STRING,"addr");
                    Record record = new WritableRecord(columns);
                    record.set(0,arr[0]);
                    record.set(1,arr[1]);
                    record.set(2,arr[2]);
                    tunnelBufferedWriter.write(record);
                }
            }
        }  catch (IOException e) {
            e.printStackTrace();
        } finally {
            tunnelBufferedWriter.close();
            uploadSession.commit();
        }


        TableSchema outSchema = new TableSchema();
        outSchema.addColumn(new Column("name",OdpsType.STRING));
        outSchema.addColumn(new Column("age",OdpsType.STRING));
        outSchema.addColumn(new Column("addr",OdpsType.STRING));
        odps.tables().create(outTableName,outSchema);
        LOG.info("----------------------------------------------------结束上传数据");
    }

    private void execute() throws OdpsException {
        LOG.info("----------------------------------------------------开始执行任务");
        JobConf job = new JobConf();
        job.setMapperClass(OdpsMapper.class);
        job.setNumReduceTasks(0);
        job.setResources("odps-m-in-demo-1.0.1.jar");

        PartitionSpec par1 = new PartitionSpec();
        par1.set(getPartitionName(),getPartitionName());
        InputUtils.addTable(TableInfo.builder().tableName(getInputTableName()).partSpec(par1).label("1").build(),job);
        PartitionSpec par2 = new PartitionSpec();
        par2.set(getPartitionName(),getPartitionName());
        InputUtils.addTable(TableInfo.builder().tableName(getInputTableName()).partSpec(par2).label("2").build(),job);
        TableInfo[] tableInfos = InputUtils.getTables(job);
        for(TableInfo tableInfo : tableInfos){
            LOG.info("----------------------------------------------------当前设置的输入：{}",new Gson().toJson(tableInfo));
        }

        OutputUtils.addTable(TableInfo.builder().tableName(getOutTableName()).build(),job);

        SessionState sessionState = SessionState.get();
        boolean isLocal = prop.getProperty("islocal").equalsIgnoreCase("true");
        if(isLocal){
            LOG.info("----------------------------------------------------使用本地模式");
            sessionState.setLocalRun(isLocal);
            //备用
            //setResources();
        }
        sessionState.setOdps(odps);
        RunningJob runningJob = JobClient.submitJob(job);
        printMaxComputeViewLogLink(runningJob);
        LOG.info("----------------------------------------------------任务已经提交");
        //判定JOB是否执行是否成功
        runningJob.waitForCompletion();
        boolean jobFinishedState = runningJob.isSuccessful();
        LOG.info("----------------------------------------------------任务执行结果：{} " ,jobFinishedState);
        System.exit(1);
    }

    /**
     * 打印阿里MaxCompute作业log日志链接
     * @param runningJob
     */
    private void printMaxComputeViewLogLink(RunningJob runningJob) {
        String instanceId = runningJob.getInstanceID();
        LOG.info("InstanceId: " + instanceId);
        Instance instance = odps.instances().get(instanceId);
        try {
            String logUrl = SessionState.get().getOdps().logview().generateLogView(instance, 168L);
            LOG.info("ODPS日志链接: 【{}】", logUrl);
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
    }

    private void setResources() throws OdpsException, IOException {
        File lib = new File("lib");
        File[] libs = lib.listFiles();
        for (File file : libs){
            String resourceName = file.getName();
            if(odps.resources().exists(resourceName)){
                odps.resources().delete(resourceName);
            }
            FileResource fileResource = new FileResource();
            fileResource.setName(resourceName);
            InputStream inputStream = new FileInputStream(file.getPath());
            odps.resources().create(fileResource,inputStream);
            LOG.info("上传jar包：{}",resourceName);
        }
    }

    private static Odps getOdps(){
        Account account = new AliyunAccount(prop.getProperty("access_id"), prop.getProperty("access_key"));
        Odps odps = new Odps(account);
        odps.setEndpoint(prop.getProperty("end_point"));
        odps.setLogViewHost(prop.getProperty("log_view_host"));
        odps.setDefaultProject(prop.getProperty("project_name"));
        return odps;
    }

    private static String getOutTableName(){
        return prop.getProperty("output_table");
    }

    private static String getInputTableName(){
        return prop.getProperty("input_table");
    }

    private static String getPartitionName(){
        return prop.getProperty("partition_name");
    }


    public static class OdpsMapper extends MapperBase {
        private Set<String> tableNameSets = new HashSet<String>();
        @Override
        public void setup(TaskContext context) throws IOException {
            super.setup(context);
        }

        @Override
        public void map(long key, Record record, TaskContext context) throws IOException {
            String label = context.getInputTableInfo().getLabel();
            //打印label值
            tableNameSets.add(label);
            System.out.println(label);
            context.write(record);
        }

        @Override
        public void cleanup(TaskContext context) throws IOException {
            for(String tableName : tableNameSets){
                System.out.println(tableName);
            }
            super.cleanup(context);
        }
    }
}
