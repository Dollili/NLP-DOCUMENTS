package rag.config;

import rag.model.Document;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class AppConfig {
    public static final String DOC_PATH = "임시경로";
    public static final List<Document> DOCUMENTS = new CopyOnWriteArrayList<>();
    public static final int MAX_WORKER = Runtime.getRuntime().availableProcessors() * 2;
}
