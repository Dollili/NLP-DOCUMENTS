package rag.config;

import rag.model.Document;

import java.util.ArrayList;
import java.util.List;

public class AppConfig {
    public static String DOC_PATH = "";
    public static final List<Document> DOCUMENTS = new ArrayList<>();
    public static final int MAX_WORKER = Runtime.getRuntime().availableProcessors() * 2;

    public static void setDocPath(String docPath) {
        DOC_PATH = docPath;
    }

    public static String getDocPath() {
        return DOC_PATH;
    }

    public static String getDocPathEnd() {
        String[] pathList = DOC_PATH.split("[/\\\\]");
        return pathList[pathList.length - 1];
    }
}