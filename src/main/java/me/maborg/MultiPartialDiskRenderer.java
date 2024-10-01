package me.maborg;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

public class MultiPartialDiskRenderer {
  private int vaoId;
  private int vboId;
  private int instanceVboId;
  private int vertexCount;
  private List<PartialDiskInstance> instances;
  private int shaderProgramId;

  public MultiPartialDiskRenderer() {
    instances = new ArrayList<>();
  }

  public void init(int slices) {
    // Generate vertices
    float[] vertices = generatePartialDiskVertices(slices);
    vertexCount = vertices.length / 2;

    // Create VAO
    vaoId = GL30.glGenVertexArrays();
    GL30.glBindVertexArray(vaoId);

    // Create VBO for vertex data
    vboId = GL15.glGenBuffers();
    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);

    FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer(vertices.length);
    vertexBuffer.put(vertices).flip();

    GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertexBuffer, GL15.GL_STATIC_DRAW);

    // Define vertex attributes
    GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 0, 0);
    GL20.glEnableVertexAttribArray(0);

    // Create VBO for instance data
    instanceVboId = GL15.glGenBuffers();
    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, instanceVboId);

    // Define instance attributes
    GL20.glVertexAttribPointer(1, 4, GL11.GL_FLOAT, false, 40, 0);  // position and angles
    GL20.glVertexAttribPointer(2, 2, GL11.GL_FLOAT, false, 40, 16); // inner and outer radius
    GL20.glVertexAttribPointer(3, 4, GL11.GL_FLOAT, false, 40, 24); // color
    GL20.glEnableVertexAttribArray(1);
    GL20.glEnableVertexAttribArray(2);
    GL20.glEnableVertexAttribArray(3);

    GL33.glVertexAttribDivisor(1, 1);
    GL33.glVertexAttribDivisor(2, 1);
    GL33.glVertexAttribDivisor(3, 1);

    // Unbind VBO and VAO
    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    GL30.glBindVertexArray(0);

    // Create and compile shaders
    createShaders();
  }

  private void createShaders() {
    // Vertex Shader
    int vertexShaderId = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
    GL20.glShaderSource(vertexShaderId,
        "#version 330 core\n" +
            "layout (location = 0) in vec2 aPos;\n" +
            "layout (location = 1) in vec4 aInstance;\n" +
            "layout (location = 2) in vec2 aRadius;\n" +
            "layout (location = 3) in vec4 aColor;\n" +
            "out vec4 vertexColor;\n" +
            "void main()\n" +
            "{\n" +
            "    float startAngle = radians(aInstance.z);\n" +
            "    float sweepAngle = radians(aInstance.w);\n" +
            "    float t = aPos.x;\n" +
            "    float angle = startAngle + t * sweepAngle;\n" +
            "    float radius = mix(aRadius.x, aRadius.y, aPos.y);\n" +
            "    vec2 position = vec2(cos(angle), sin(angle)) * radius + aInstance.xy;\n" +
            "    gl_Position = vec4(position, 0.0, 1.0);\n" +
            "    vertexColor = aColor;\n" +
            "}\n"
    );
    GL20.glCompileShader(vertexShaderId);

    // Fragment Shader
    int fragmentShaderId = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
    GL20.glShaderSource(fragmentShaderId,
        "#version 330 core\n" +
            "in vec4 vertexColor;\n" +
            "out vec4 FragColor;\n" +
            "void main()\n" +
            "{\n" +
            "    FragColor = vertexColor;\n" +
            "}\n"
    );
    GL20.glCompileShader(fragmentShaderId);

    // Link shaders
    shaderProgramId = GL20.glCreateProgram();
    GL20.glAttachShader(shaderProgramId, vertexShaderId);
    GL20.glAttachShader(shaderProgramId, fragmentShaderId);
    GL20.glLinkProgram(shaderProgramId);

    // Clean up
    GL20.glDeleteShader(vertexShaderId);
    GL20.glDeleteShader(fragmentShaderId);
  }

  public void add(float centerX, float centerY, float startAngle, float sweepAngle, float innerRadius, float outerRadius, float r, float g, float b, float a) {
    instances.add(new PartialDiskInstance(centerX, centerY, startAngle, sweepAngle, innerRadius, outerRadius, r, g, b, a));
  }

  public void updateInstanceData() {
    if (instances.isEmpty()) {
      return;
    }

    float[] instanceData = new float[instances.size() * 10];
    int index = 0;
    for (PartialDiskInstance instance : instances) {
      instanceData[index++] = instance.centerX;
      instanceData[index++] = instance.centerY;
      instanceData[index++] = instance.startAngle;
      instanceData[index++] = instance.sweepAngle;
      instanceData[index++] = instance.innerRadius;
      instanceData[index++] = instance.outerRadius;
      instanceData[index++] = instance.r;
      instanceData[index++] = instance.g;
      instanceData[index++] = instance.b;
      instanceData[index++] = instance.a;
    }

    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, instanceVboId);
    FloatBuffer instanceBuffer = BufferUtils.createFloatBuffer(instanceData.length);
    instanceBuffer.put(instanceData).flip();
    GL15.glBufferData(GL15.GL_ARRAY_BUFFER, instanceBuffer, GL15.GL_DYNAMIC_DRAW);
    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
  }

  public void render() {
    if (instances.isEmpty()) {
      return;
    }

    GL20.glUseProgram(shaderProgramId);
    GL30.glBindVertexArray(vaoId);
    GL31.glDrawArraysInstanced(GL11.GL_TRIANGLE_STRIP, 0, vertexCount, instances.size());
    GL30.glBindVertexArray(0);
    GL20.glUseProgram(0);
  }

  public void cleanup() {
    GL20.glDisableVertexAttribArray(0);
    GL20.glDisableVertexAttribArray(1);
    GL20.glDisableVertexAttribArray(2);
    GL20.glDisableVertexAttribArray(3);
    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    GL15.glDeleteBuffers(vboId);
    GL15.glDeleteBuffers(instanceVboId);
    GL30.glBindVertexArray(0);
    GL30.glDeleteVertexArrays(vaoId);
    GL20.glDeleteProgram(shaderProgramId);
  }

  private float[] generatePartialDiskVertices(int slices) {
    float[] vertices = new float[(slices + 1) * 4];
    int index = 0;

    for (int i = 0; i <= slices; i++) {
      float t = (float) i / slices;

      // Inner vertex
      vertices[index++] = t;
      vertices[index++] = 0.0f;

      // Outer vertex
      vertices[index++] = t;
      vertices[index++] = 1.0f;
    }

    return vertices;
  }

  private static class PartialDiskInstance {
    float centerX, centerY, startAngle, sweepAngle, innerRadius, outerRadius, r, g, b, a;

    PartialDiskInstance(float centerX, float centerY, float startAngle, float sweepAngle, float innerRadius, float outerRadius, float r, float g, float b, float a) {
      this.centerX = centerX;
      this.centerY = centerY;
      this.startAngle = startAngle;
      this.sweepAngle = sweepAngle;
      this.innerRadius = innerRadius;
      this.outerRadius = outerRadius;
      this.r = r;
      this.g = g;
      this.b = b;
      this.a = a;
    }
  }
}