package rag.service;

import org.json.JSONArray;
import org.json.JSONObject;
import rag.config.DataType;
import rag.model.CallBack;
import rag.model.Document;
import rag.model.Tracker;
import rag.util.FileUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

import static rag.config.AppConfig.*;

public class IndexService {
    private static final String SAVE_PATH = new DataType().getSaveFormat();

    public static String buildIndex(Tracker tracker, CallBack func) {
        getDocuments(tracker, func);
        return saveIndex();
    }

    // 문서 인덱싱 시작
    public static void getDocuments(Tracker tracker, CallBack func) {
        ForkJoinPool pool = new ForkJoinPool(MAX_WORKER);
        pool.invoke(new FileUtils.FolderTask(new File(DOC_PATH), 0, tracker, func));
        pool.shutdown();
    }

    public static boolean shouldRebuildIndex() {
        try {
            File file = new File(SAVE_PATH);
            // 인덱스 파일이 없으면 재구축
            if (!file.exists()) {
                return true;
            }
            long indexAge = System.currentTimeMillis() - file.lastModified();
            if (indexAge > 3L * 24 * 60 * 60 * 1000) { // 3일
                System.out.println("3일 경과된 인덱스 ::: 재인덱싱");
                return true;
            }
            return false;
        } catch (Exception e) {
            System.out.println("인덱스 상태 확인 실패 ::: " + e.getMessage());
            return true;
        }
    }

    // 인덱스 저장
    public static String saveIndex() {
        try {
            if (SAVE_PATH.endsWith("json")) {
                JSONArray jsonArray = new JSONArray();

                for (Document doc : DOCUMENTS) {
                    JSONObject jsonDoc = new JSONObject().put("fileName", doc.fileName).put("path", doc.path);
                    jsonArray.put(jsonDoc);
                }

                try (FileWriter writer = new FileWriter(SAVE_PATH)) {
                    writer.write(jsonArray.toString(2));
                }
            } else {
                try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(SAVE_PATH))) {
                    oos.writeObject(new ArrayList<>(DOCUMENTS));
                }
            }
            return "인덱스 파일 저장: " + SAVE_PATH;
        } catch (Exception e) {
            return "인덱스 저장 실패: " + e.getMessage();
        }
    }

    // 인덱스 로드
    @SuppressWarnings("unchecked")
    public static void loadIndex(Tracker tracker, CallBack func) {
        System.out.println("기존 인덱스 로딩 중...");
        try {
            if (SAVE_PATH.endsWith("json")) {
                String content = Files.readString(Paths.get(SAVE_PATH));
                JSONArray jsonArray = new JSONArray(content);
                DOCUMENTS.clear();
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject jsonDoc = jsonArray.getJSONObject(i);

                    Document doc = new Document(jsonDoc.getString("fileName"), jsonDoc.getString("path"));
                    DOCUMENTS.add(doc);
                }
            } else {
                try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(SAVE_PATH))) {
                    List<Document> loadedDocs = (List<Document>) ois.readObject();
                    DOCUMENTS.clear();
                    DOCUMENTS.addAll(loadedDocs);
                }
            }
        } catch (Exception e) {
            System.err.println("인덱스 로드 실패: " + e.getMessage());
            buildIndex(tracker, func);
        }
    }

}
