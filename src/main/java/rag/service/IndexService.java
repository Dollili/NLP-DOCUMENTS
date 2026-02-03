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
        ForkJoinPool pool = null;
        try {
            DOCUMENTS.clear();

            pool = new ForkJoinPool(MAX_WORKER);
            pool.invoke(new FileUtils.FolderTask(new File(DOC_PATH), 0, func));
        } finally {
            if (pool != null) {
                pool.shutdown();
                try {
                    // 최대 30초 대기
                    if (!pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)) {
                        System.err.println("경고: 인덱싱 작업이 제한 시간 내에 완료되지 않았습니다.");
                        pool.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    pool.shutdownNow();
                }
            }
        }
    }

    public static boolean shouldRebuildIndex(String path) {
        if (path == null || path.trim().isEmpty()) {
            System.err.println("인덱스 경로가 비어있습니다.");
            return true;
        }
        
        try {
            String fullPath = sysPath + File.separator + path + SAVE_PATH;
            File file = new File(fullPath);
            
            // 인덱스 파일이 없으면 재구축
            if (!file.exists()) {
                System.out.println("인덱스 파일이 없습니다. 재구축이 필요합니다.");
                return true;
            }
            
            // 파일 크기 검증
            if (file.length() == 0) {
                System.out.println("인덱스 파일이 비어있습니다. 재구축이 필요합니다.");
                return true;
            }
            
            // 1일 경과 확인
            long indexAge = System.currentTimeMillis() - file.lastModified();
            long oneDayMillis = 24 * 60 * 60 * 1000L;
            
            if (indexAge > oneDayMillis) {
                System.out.println("인덱스가 1일 이상 경과했습니다. 업데이트가 필요합니다.");
                return true;
            }
            
            return false;
        } catch (SecurityException e) {
            System.err.println("인덱스 파일 접근 권한이 없습니다: " + e.getMessage());
            return true;
        } catch (Exception e) {
            System.err.println("인덱스 상태 확인 실패: " + e.getMessage());
            return true;
        }
    }

    // 인덱스 저장
    public static String saveIndex(String path) {
        if (path == null || path.trim().isEmpty()) {
            return "인덱스 저장 실패: 경로가 비어있습니다.";
        }
        
        if (DOCUMENTS.isEmpty()) {
            return "인덱스 저장 실패: 저장할 문서가 없습니다.";
        }
        
        System.out.println("인덱스 생성 중...");

        try {
            // 디렉토리 생성
            if (!folder.exists()) {
                boolean created = folder.mkdirs();
                if (!created) {
                    return "인덱스 저장 실패: 디렉토리 생성에 실패했습니다.";
                }
            }

            String fullPath = sysPath + File.separator + path + SAVE_PATH;
            File indexFile = new File(fullPath);

            if (SAVE_PATH.endsWith("json")) {
                JSONArray jsonArray = new JSONArray();

                for (Document doc : DOCUMENTS) {
                    if (doc == null) {
                        System.err.println("경고: null 문서가 발견되어 건너뜁니다.");
                        continue;
                    }
                    
                    JSONObject jsonDoc = new JSONObject()
                            .put("fileName", doc.fileName != null ? doc.fileName : "")
                            .put("path", doc.path != null ? doc.path : "");
                    jsonArray.put(jsonDoc);
                }

                try (FileWriter writer = new FileWriter(fullPath)) {
                    writer.write(jsonArray.toString(2));
                    writer.flush();
                }
                
                // 파일 저장 확인
                if (!indexFile.exists() || indexFile.length() == 0) {
                    return "인덱스 저장 실패: 파일이 제대로 생성되지 않았습니다.";
                }
                
            } else {
                try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(fullPath))) {
                    oos.writeObject(new ArrayList<>(DOCUMENTS));
                    oos.flush();
                }
                
                if (!indexFile.exists() || indexFile.length() == 0) {
                    return "인덱스 저장 실패: 파일이 제대로 생성되지 않았습니다.";
                }
            }
            
            System.out.println("인덱스 저장 완료: " + DOCUMENTS.size() + "개 문서");
            return "인덱스 파일 저장 완료: " + fullPath + " (" + DOCUMENTS.size() + "개 문서)";
            
        } catch (IOException e) {
            System.err.println("인덱스 저장 IO 오류: " + e.getMessage());
            return "인덱스 저장 실패: 파일 쓰기 오류 - " + e.getMessage();
        } catch (SecurityException e) {
            System.err.println("인덱스 저장 권한 오류: " + e.getMessage());
            return "인덱스 저장 실패: 파일 접근 권한 없음 - " + e.getMessage();
        } catch (Exception e) {
            System.err.println("인덱스 저장 예외 발생: " + e.getMessage());
            e.printStackTrace();
            return "인덱스 저장 실패: " + e.getMessage();
        }
    }

    // 인덱스 로드
    @SuppressWarnings("unchecked")
    public static void loadIndex(String path, CallBack func) {
        if (path == null || path.trim().isEmpty()) {
            System.err.println("인덱스 로드 실패: 경로가 비어있습니다.");
            return;
        }
        
        System.out.println("기존 인덱스 로딩 중...");
        String fullPath = sysPath + File.separator + path + SAVE_PATH;
        File indexFile = new File(fullPath);

        // 파일 존재 확인
        if (!indexFile.exists()) {
            System.err.println("인덱스 파일이 존재하지 않습니다: " + fullPath);
            System.out.println("새로운 인덱스를 생성합니다.");
            buildIndex(path, func);
            return;
        }
        
        // 파일 크기 확인
        if (indexFile.length() == 0) {
            System.err.println("인덱스 파일이 비어있습니다: " + fullPath);
            System.out.println("새로운 인덱스를 생성합니다.");
            buildIndex(path, func);
            return;
        }

        try {
            if (SAVE_PATH.endsWith("json")) {
                // JSON 형식 로드
                String content = Files.readString(Paths.get(fullPath));
                
                if (content.trim().isEmpty()) {
                    throw new IOException("JSON 파일이 비어있습니다.");
                }
                
                JSONArray jsonArray = new JSONArray(content);
                DOCUMENTS.clear();
                
                for (int i = 0; i < jsonArray.length(); i++) {
                    try {
                        JSONObject jsonDoc = jsonArray.getJSONObject(i);
                        
                        String fileName = jsonDoc.optString("fileName", "");
                        String docPath = jsonDoc.optString("path", "");
                        
                        if (fileName.isEmpty() && docPath.isEmpty()) {
                            System.err.println("경고: 빈 문서 정보가 발견되어 건너뜁니다. (인덱스: " + i + ")");
                            continue;
                        }
                        
                        Document doc = new Document(fileName, docPath);
                        DOCUMENTS.add(doc);
                        
                    } catch (org.json.JSONException e) {
                        System.err.println("JSON 파싱 오류 (인덱스 " + i + "): " + e.getMessage());
                    }
                }
                
                System.out.println("인덱스 로드 완료: " + DOCUMENTS.size() + "개 문서");
                
            } else {
                // 직렬화 형식 로드
                try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(fullPath))) {
                    Object obj = ois.readObject();
                    
                    if (!(obj instanceof List)) {
                        throw new IOException("인덱스 파일 형식이 올바르지 않습니다.");
                    }
                    
                    List<Document> loadedDocs = (List<Document>) obj;
                    DOCUMENTS.clear();
                    DOCUMENTS.addAll(loadedDocs);
                    
                    System.out.println("인덱스 로드 완료: " + DOCUMENTS.size() + "개 문서");
                }
            }
            
            // 로드 후 검증
            if (DOCUMENTS.isEmpty()) {
                System.err.println("경고: 로드된 문서가 없습니다.");
                System.out.println("새로운 인덱스를 생성합니다.");
                buildIndex(path, func);
            }
            
        } catch (IOException e) {
            System.err.println("인덱스 로드 IO 오류: " + e.getMessage());
            System.out.println("새로운 인덱스를 생성합니다.");
            buildIndex(path, func);
        } catch (ClassNotFoundException e) {
            System.err.println("인덱스 로드 실패: 클래스를 찾을 수 없습니다. - " + e.getMessage());
            System.out.println("새로운 인덱스를 생성합니다.");
            buildIndex(path, func);
        } catch (org.json.JSONException e) {
            System.err.println("JSON 파싱 오류: " + e.getMessage());
            System.out.println("인덱스 파일이 손상되었습니다. 새로운 인덱스를 생성합니다.");
            buildIndex(path, func);
        } catch (Exception e) {
            System.err.println("인덱스 로드 중 예외 발생: " + e.getMessage());
            e.printStackTrace();
            System.out.println("새로운 인덱스를 생성합니다.");
            buildIndex(path, func);
        }
    }

}
