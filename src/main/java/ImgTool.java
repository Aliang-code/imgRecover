import com.thebuzzmedia.exiftool.ExifTool;
import com.thebuzzmedia.exiftool.ExifToolBuilder;
import com.thebuzzmedia.exiftool.ExifToolOptions;
import com.thebuzzmedia.exiftool.Tag;
import com.thebuzzmedia.exiftool.core.StandardFormat;
import com.thebuzzmedia.exiftool.core.StandardOptions;
import com.thebuzzmedia.exiftool.core.StandardTag;
import javafx.util.Pair;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class ImgTool {
    private static final ExifTool exifTool = detect();
    private static ExifToolOptions options = new SupportUtf8FileNameOptions(StandardOptions.builder().withFormat(StandardFormat.NUMERIC).withOverwriteOriginal().build());

    private static ExifTool detect() {
        return new ExifToolBuilder()
                .withPoolSize(10)  // Allow 10 process
                .enableStayOpen()
                .build();
    }

    public static Map<Tag, String> parse(File image) throws Exception {
        return exifTool.getImageMeta(image, options);
    }

    public static Map<Tag, String> parse(File image, Tag... tags) throws Exception {
        return exifTool.getImageMeta(image, options, Arrays.asList(tags));
    }

    public static Map<Tag, String> parse(File image, List<? extends Tag> tags) throws Exception {
        return exifTool.getImageMeta(image, options, tags);
    }


    private static Map<Tag, String> parse(String image) throws Exception {
        return parse(new File(image));
    }

    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(10);

        String sourcePath = "D:\\相册\\LR\\相册\\川西";
        String targetPath = "D:\\相册\\已分类\\202310-国庆-川西";
        File sourceDir = new File(sourcePath);
        File targetDir = new File(targetPath);
        if (!sourceDir.isDirectory() || !sourceDir.canRead()) {
            throw new FileNotFoundException("can not open source dir:" + sourcePath);
        }
        if (!targetDir.isDirectory() || !targetDir.canRead()) {
            throw new FileNotFoundException("can not open target dir:" + targetPath);
        }

        try {
            File[] sourceImages = sourceDir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.endsWith(".jpg");
                }
            });
            File[] targetRaw = targetDir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.endsWith(".NEF");
                }
            });
            File[] targetJpg = targetDir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.endsWith(".jpg");
                }
            });
            List<StandardTag> standardTags = new ArrayList<>(Arrays.asList(StandardTag.values()));
            standardTags.removeAll(Arrays.asList(
                    StandardTag.DIGITAL_ZOOM_RATIO,
                    StandardTag.IMAGE_WIDTH,
                    StandardTag.IMAGE_HEIGHT,
                    StandardTag.X_RESOLUTION,
                    StandardTag.Y_RESOLUTION,
                    StandardTag.ORIENTATION,
                    StandardTag.ROTATION,
                    StandardTag.FILE_TYPE,
                    StandardTag.FILE_SIZE,
                    StandardTag.AVG_BITRATE,
                    StandardTag.MIME_TYPE,
                    StandardTag.IMAGE_DATA_SIZE,
                    StandardTag.MEGA_PIXELS,
                    StandardTag.QUALITY));

            CountDownLatch latch = new CountDownLatch(sourceImages.length);
            Map<Long, List<Pair<File, Map<Tag, String>>>> sourceMap = new ConcurrentHashMap<>();
            for (final File image : sourceImages) {
                executor.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Map<Tag, String> tagMap = parse(image, standardTags);
                            //                            System.out.println(image.getPath() + "----Tags: " + tagMap);
                            String createDateStr = tagMap.get(StandardTag.CREATE_DATE);
                            if (createDateStr != null) {
                                SimpleDateFormat df = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss");
                                long createTime = df.parse(createDateStr).getTime();
                                List<Pair<File, Map<Tag, String>>> list;
                                if ((list = sourceMap.get(createTime)) != null) {
                                    System.out.println("source image repeat:" + image.getPath() + "----" + list.get(0).getKey().getPath());
                                    list.add(new Pair<>(image, tagMap));
                                } else {
                                    list = Collections.synchronizedList(new ArrayList<>());
                                    list.add(new Pair<>(image, tagMap));
                                    sourceMap.put(createTime, list);
                                }
                            } else {
                                System.out.println("source image exif not found:" + image.getPath());
                            }
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        } finally {
                            latch.countDown();
                        }
                    }
                });
            }
            latch.await();
            CountDownLatch latch2 = new CountDownLatch(targetJpg.length);
            Map<Long, List<File>> targetMap = new ConcurrentHashMap<>();
            for (final File image : targetJpg) {
                executor.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Map<Tag, String> tagMap = parse(image, StandardTag.CREATE_DATE);
                            //                            System.out.println(image.getPath() + "----Tags: " + tagMap);
                            String createDateStr = tagMap.get(StandardTag.CREATE_DATE);
                            if (createDateStr != null) {
                                SimpleDateFormat df = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss");
                                long createTime = df.parse(createDateStr).getTime();
                                List<File> list;
                                if ((list = targetMap.get(createTime)) != null) {
                                    System.out.println("target jpg repeat:" + image.getPath() + "----" + list.get(0).getPath());
                                    list.add(image);
                                } else {
                                    list = Collections.synchronizedList(new ArrayList<>());
                                    list.add(image);
                                    targetMap.put(createTime, list);
                                }
                            } else {
                                System.out.println("target jpg exif not found:" + image.getPath());
                            }
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        } finally {
                            latch2.countDown();
                        }
                    }
                });
            }
            latch2.await();
            System.out.println("source size:" + sourceMap.size() + ", targetJpg size:" + targetMap.size() + ", targetRaw size:" + targetRaw.length);
            String tmpDir = targetPath + File.separator + "tmp";
            new File(tmpDir).mkdir();
            CountDownLatch latch3 = new CountDownLatch(targetRaw.length);
            for (final File image : targetRaw) {
                executor.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Map<Tag, String> tagMap = parse(image, StandardTag.CREATE_DATE);
                            String createDateStr = tagMap.get(StandardTag.CREATE_DATE);
                            if (createDateStr != null) {
                                SimpleDateFormat df = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss");
                                long createTime = df.parse(createDateStr).getTime();
                                List<Pair<File, Map<Tag, String>>> sourceList;
                                if ((sourceList = sourceMap.get(createTime)) != null && !sourceList.isEmpty()) {
                                    Pair<File, Map<Tag, String>> source = sourceList.remove(0);
                                    System.out.println("raw image match source:" + image.getPath() + "----" + source.getKey().getPath());
                                    List<File> targetList;
                                    if ((targetList = targetMap.get(createTime)) != null && !targetList.isEmpty()) {
                                        File target = targetList.remove(0);
                                        System.out.println("jpg image match source:" + target.getPath() + "----" + source.getKey().getPath());
                                        target.renameTo(new File(targetDir + File.separator + source.getKey().getName()));
                                    } else {
                                        File target = new File(image.getPath().replace(".NEF", ".jpg"));
                                        if (target.exists()) {
                                            System.out.println("copy to same name jpg:" + target.getPath() + "----" + image.getPath());
                                            tagMap = new HashMap<>(source.getValue());
                                            tagMap.put(StandardTag.DATE_TIME_ORIGINAL, tagMap.get(StandardTag.CREATE_DATE));
                                            exifTool.setImageMeta(target, options, tagMap);
                                            target.renameTo(new File(targetDir + File.separator + source.getKey().getName()));
                                        } else {
                                            Files.copy(source.getKey().toPath(), new File(targetDir + File.separator + source.getKey().getName()).toPath());
                                            System.out.println("copy source jpg to:" + target.getPath() + "----" + image.getPath());
                                        }
                                    }
                                    image.renameTo(new File(targetDir + File.separator + source.getKey().getName().replace(".jpg", ".NEF")));
                                } else {
                                    System.out.println("target image not match, mv to tmp dir:" + image.getPath());
                                    image.renameTo(new File(tmpDir + File.separator + image.getName()));
                                }
                            } else {
                                System.out.println("target image exif not found, mv to tif:" + image.getPath());
                                image.renameTo(new File(tmpDir + File.separator + image.getName().replace(".NEF",".tif")));
                            }
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        } finally {
                            latch3.countDown();
                        }
                    }
                });
            }
            latch3.await();
            System.out.println("all task done");
        } finally {
            executor.shutdown();
            exifTool.close();
        }
    }
}
