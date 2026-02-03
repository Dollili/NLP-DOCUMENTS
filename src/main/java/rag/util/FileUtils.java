package rag.util;

import org.apache.commons.io.FilenameUtils;
import rag.model.CallBack;
import rag.model.Document;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.RecursiveAction;

import static rag.config.AppConfig.DOCUMENTS;
import static rag.config.AppConfig.DOC_PATH;

public class FileUtils {
    private static final int MAX_DEPTHS = 4;

    /**
     * 파일 경로에서 폴더 경로를 추출하여 탐색기에서 엽니다.
     * @param fullPath 전체 파일 경로
     * @return 성공 여부
     */
    public static boolean openFolderInExplorer(String fullPath) {
        try {
            File file = new File(fullPath);
            File folder;
            
            // 파일이면 부모 폴더를, 폴더면 그 폴더를 엽니다
            if (file.isFile()) {
                folder = file.getParentFile();
            } else if (file.isDirectory()) {
                folder = file;
            } else {
                // 파일이 존재하지 않으면 경로를 분석하여 폴더 추출
                String path = file.getAbsolutePath();
                int lastSeparator = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
                if (lastSeparator > 0) {
                    folder = new File(path.substring(0, lastSeparator));
                } else {
                    System.err.println("유효하지 않은 경로: " + fullPath);
                    return false;
                }
            }
            
            if (folder == null || !folder.exists()) {
                System.err.println("폴더가 존재하지 않습니다: " + folder);
                return false;
            }
            
            // Desktop API를 사용하여 폴더 열기
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.OPEN)) {
                    desktop.open(folder);
                    System.out.println("폴더 열기 성공: " + folder.getAbsolutePath());
                    return true;
                }
            }
            
            // Desktop API가 지원되지 않으면 OS별 명령어 실행
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                // Windows
                Runtime.getRuntime().exec("explorer.exe /select," + folder.getAbsolutePath());
            } else if (os.contains("mac")) {
                // macOS
                Runtime.getRuntime().exec("open " + folder.getAbsolutePath());
            } else if (os.contains("nix") || os.contains("nux")) {
                // Linux
                Runtime.getRuntime().exec("xdg-open " + folder.getAbsolutePath());
            }
            
            return true;
        } catch (IOException e) {
            System.err.println("폴더 열기 실패: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static class FolderTask extends RecursiveAction {
        private final File folder;
        private final int depth;
        private final CallBack callback;

        public FolderTask(File folder, int depth, CallBack func) {
            this.folder = folder;
            this.depth = depth;
            this.callback = func;
        }

        @Override
        protected void compute() {
            if (isFolderInvalid(depth, folder)) {
                return;
            }
            /*File[] files = folder.listFiles();
            if (files == null)
                return;*/

            List<FolderTask> subTasks = new java.util.ArrayList<>();

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder.toPath())) {
                for (Path path : stream) {
                    File file = path.toFile();
                    try {
                        if (file.isFile() && allowed(file)) {
                            String filePath = file.getAbsolutePath().substring(DOC_PATH.length());
                            synchronized (DOCUMENTS) {
                                DOCUMENTS.add(new Document(file.getName(), filePath));
                            }
                            if (callback != null) {
                                callback.callBackCnt("TOTAL :: " + DOCUMENTS.size()); // UI에 탐색한 수 출력
                            }
                        } else if (file.isDirectory() && !isSystemDirectory(file)) {
                            subTasks.add(new FolderTask(file, depth + 1, callback)); // 하위 디렉토리를 새로운 태스크로 추가
                        }
                    } catch (Exception e) {
                        System.err.println("파일 탐색 중 오류 발생: " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                System.err.println("디렉토리 접근 실패: " + e.getMessage());
            }

            // 병렬 실행
            if (!subTasks.isEmpty()) {
                invokeAll(subTasks);
            }
        }
    }

    public static boolean isFolderInvalid(String path) {
        File folder = new File(path);
        return !folder.exists() || !folder.canRead() || folder.isHidden();
    }

    public static boolean isFolderInvalid(int depth, File folder) {
        return depth > MAX_DEPTHS || !folder.exists() || !folder.canRead() || folder.isHidden();
    }

    public static boolean isSystemDirectory(File file) {
        String name = file.getName().toLowerCase();
        return name.startsWith(".") || name.equals("system32") || name.equals("windows") || name.equals("program files") || name.equals("program files (x86)") || name.contains("temp") || name.contains("cache");
    }

    public static boolean allowed(File file) {
        String ext = FilenameUtils.getExtension(file.getName()).toLowerCase();
        Set<String> allowedExts = Set.of(
                // 문서
                "txt", "pdf", "docx", "pptx", "hwp", "xlsx", "csv",
                // 실행 파일
                "exe", "msi", "dmg", "app",
                "html", "xml",
                // 기타
                "md", "rtf", "odt");
        return allowedExts.contains(ext);
    }

    // hugging face 사용 시 전처리 - 속도, 정확도 약간 향상
/*    public static String extractMeaningfulText(String fileName, String path) {
        String nameWithoutExt = FilenameUtils.getBaseName(fileName);

        String cleanName = nameWithoutExt.replaceAll("[_-]", " ");

        // 경로에서 마지막 폴더명만 추출
        String[] pathParts = path.split("[/\\\\]");
        String parentFolder = "";
        if (pathParts.length > 1) {
            parentFolder = pathParts[pathParts.length - 2]; // 파일의 부모 폴더
            parentFolder = parentFolder.replaceAll("[_-]", " ");
        }

        String result = cleanName;
        if (!parentFolder.isEmpty() && !isSystemDirectory(new File(parentFolder))) {
            result = cleanName + " " + parentFolder;
        }

        if (result.length() > 50) {
            result = result.substring(0, 50);
        }

        return result.trim();
    }*/
}
