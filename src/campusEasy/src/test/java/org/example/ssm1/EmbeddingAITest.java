package org.example.ssm1;

import io.qdrant.client.QdrantClient;
import org.example.ssm1.Util.VectorUtil;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.List;

@SpringBootTest
public class EmbeddingAITest {

    @Autowired
    private EmbeddingModel embeddingModel;
    @Test
    public void testEmbedding() {
//        String query = "国际新闻";
//        String[] text = new String[] {
//                "美国大选结果出炉，特朗普再次当选总统",
//                "欧盟通过新法案，限制大型科技公司垄断行为",
//                "水立方：恢复游泳、戏水乐园等水上项目运营",
//                "中国乡村振兴取得显著成果",
//                "俄罗斯与乌克兰达成停火协议，缓解边境紧张局势",
//                "科目二考试内容改革"
//        };
//        float[] queryVector = embeddingModel.embed(query);
//        System.out.println("向量长度"+queryVector.length);
//        List<float[]> textVectors = embeddingModel.embed(Arrays.asList(text));
//        for(float[] vector:textVectors){
//            System.out.println(VectorUtil.cosineSimilarity(queryVector, vector)+" "+VectorUtil.euclideanDistance(queryVector, vector));
//        }
        System.out.println(QdrantClient.class.getPackage().getImplementationVersion());
    }
}
