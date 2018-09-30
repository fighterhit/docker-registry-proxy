import cn.ac.iie.common.DockerConfig;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.api.model.PushResponseItem;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.github.dockerjava.core.command.PushImageResultCallback;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Fighter Created on 2018/9/30.
 */
public class DockerAPITest {
    private DockerClient dockerClient = DockerConfig.getDockerClient();
    private AuthConfig authConfig = new AuthConfig()
            .withUsername("admin")
            .withPassword("Harbor12345")
            .withRegistryAddress("http://192.168.11.112/v2/");

    @Test
    void testLoad() {
        Map<String, String> map = null;
        try {
            String imagePath = "G:\\IdeaProjects\\docker-registry-proxy\\src\\test\\java\\image_1.0.tar";
            File file = new File(imagePath.trim());
            String fileName = file.getName();
            String[] imageNameAndTag = fileName.substring(0, fileName.lastIndexOf('.')).split("_");
            InputStream uploadStream = new FileInputStream(file);
            dockerClient.loadImageCmd(uploadStream).exec();
            map = new HashMap<>();
            map.put("imageName", imageNameAndTag[0]);
            map.put("tag", imageNameAndTag[1]);
            System.out.println(map);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void tag() {
        try {
            dockerClient.tagImageCmd("docker-java/load:1.0", "docker-java/load", "2.0").exec();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void push() {
        try {
            //push image
            dockerClient.pushImageCmd("192.168.11.112/hlg_web/busybox:test")
                    .withAuthConfig(authConfig)
                    .exec(new MyPushImageResultCallback("192.168.11.112/hlg_web/busybox:harbortest"))
                    .awaitSuccess();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void pull() {
        try {
            //pull image
            dockerClient.pullImageCmd("192.168.11.112/hlg_web/busybox:harbortest")
                    .exec(new MyPullImageResultCallback("192.168.11.112/hlg_web/busybox:harbortest"))
                    .awaitSuccess();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * get all the images from the remote docker engine
     *
     * @return
     */
    @Test
    void getImages() {
        List<Image> images = null;
        try {
            images = dockerClient.listImagesCmd().exec();
        } catch (Exception e) {
        }
        System.out.println(images);
    }

    class MyPushImageResultCallback extends PushImageResultCallback {

        private String imageNameAndTag;

        public MyPushImageResultCallback(String imageNameAndTag) {
            this.imageNameAndTag = imageNameAndTag;
        }

        @Override
        public void onNext(PushResponseItem item) {
            super.onNext(item);
        }

        @Override
        public void onComplete() {
            super.onComplete();
        }
    }

    class MyPullImageResultCallback extends PullImageResultCallback {

        private String imageNameAndTag;

        public MyPullImageResultCallback(String imageNameAndTag) {
            this.imageNameAndTag = imageNameAndTag;
        }

        @Override
        public void onNext(PullResponseItem item) {
            super.onNext(item);
        }

        @Override
        public void onComplete() {
            super.onComplete();
        }
    }
}