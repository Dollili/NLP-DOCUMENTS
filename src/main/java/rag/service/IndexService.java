package rag.service;

import org.json.JSONArray;
import org.json.JSONObject;
import rag.config.DataType;
import rag.model.CallBack;
import rag.model.Document;
import rag.util.FileUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

import static rag.config.AppConfig.*;

public class IndexService {
    private static final String SAVE_PATH = new DataType().getFileExt();
    private static final String sysPath = System.getProperty("user.dir") + File.separator + "temp";
    private static final File folder = new File(sysPath);

    public static String buildIndex(String path, CallBack func) {
        getDocuments(func);
        return saveIndex(path);
    }

    // 문서 인덱싱 시작
    public static void getDocuments(CallBack func) {
        ForkJoinPool pool = new ForkJoinPool(MAX_WORKER);
        pool.invoke(new FileUtils.FolderTask(new File(DOC_PATH), 0, func));
        pool.shutdown();
    }

    public static boolean shouldRebuildIndex(String path) {
        try {
            String fullPath = sysPath + File.separator + path + SAVE_PATH;
            File file = new File(fullPath);
            // 인덱스 파일이 없으면 재구축
            if (!file.exists()) {
                return true;
            }
            long indexAge = System.currentTimeMillis() - file.lastModified();
            if (indexAge > 24 * 60 * 60 * 1000) { // 1일
                System.out.println("1일 경과 ::: 업데이트");
                return true;
            }
            return false;
        } catch (Exception e) {
            System.out.println("인덱스 상태 확인 실패 ::: " + e.getMessage());
            return true;
        }
    }

    // 인덱스 저장
    public static String saveIndex(String path) {
        System.out.println("인덱스 생성 중...");

        if (!folder.exists()) {
            folder.mkdirs();
        }

        String fullPath = sysPath + File.separator + path + SAVE_PATH;

        try {
            if (SAVE_PATH.endsWith("json")) {
                JSONArray jsonArray = new JSONArray();

                for (Document doc : DOCUMENTS) {
                    JSONObject jsonDoc = new JSONObject().put("fileName", doc.fileName).put("path", doc.path);
                    jsonArray.put(jsonDoc);
                }

                try (FileWriter writer = new FileWriter(fullPath)) {
                    writer.write(jsonArray.toString(2));
                }
            } else {
                try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(fullPath))) {
                    oos.writeObject(new ArrayList<>(DOCUMENTS));
                }
            }
            return "인덱스 파일 저장: " + fullPath +SAVE_PATH;
        } catch (Exception e) {
            return "인덱스 저장 실패: " + e.getMessage();
        }
    }

    // 인덱스 로드
    @SuppressWarnings("unchecked")
    public static void loadIndex(String path, CallBack func) {
        System.out.println("기존 인덱스 로딩 중...");
        String fullPath = sysPath + File.separator + path + SAVE_PATH;

        try {
            if (SAVE_PATH.endsWith("json")) {
                String content = Files.readString(Paths.get(fullPath));
                JSONArray jsonArray = new JSONArray(content);
                DOCUMENTS.clear();
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject jsonDoc = jsonArray.getJSONObject(i);

                    Document doc = new Document(jsonDoc.getString("fileName"), jsonDoc.getString("path"));
                    DOCUMENTS.add(doc);
                }
            } else {
                try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(fullPath))) {
                    List<Document> loadedDocs = (List<Document>) ois.readObject();
                    DOCUMENTS.clear();
                    DOCUMENTS.addAll(loadedDocs);
                }
            }
        } catch (Exception e) {
            System.err.println("인덱스 로드 실패: " + e.getMessage());
            buildIndex(path, func);
        }
    }

}
