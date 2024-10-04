/***************************************************
 * Marco Borgna 2024, all rights reserved          *
 ***************************************************/
package me.maborg;

import static me.maborg.ConcurrentHierarchicalFolderSizeCalculator.FolderInfo;
import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MAJOR;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MINOR;
import static org.lwjgl.glfw.GLFW.GLFW_FALSE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_1;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_CORE_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;
import static org.lwjgl.glfw.GLFW.GLFW_RESIZABLE;
import static org.lwjgl.glfw.GLFW.GLFW_TRUE;
import static org.lwjgl.glfw.GLFW.GLFW_VISIBLE;
import static org.lwjgl.glfw.GLFW.glfwCreateWindow;
import static org.lwjgl.glfw.GLFW.glfwDefaultWindowHints;
import static org.lwjgl.glfw.GLFW.glfwDestroyWindow;
import static org.lwjgl.glfw.GLFW.glfwGetPrimaryMonitor;
import static org.lwjgl.glfw.GLFW.glfwGetVideoMode;
import static org.lwjgl.glfw.GLFW.glfwGetWindowSize;
import static org.lwjgl.glfw.GLFW.glfwInit;
import static org.lwjgl.glfw.GLFW.glfwMakeContextCurrent;
import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwSetErrorCallback;
import static org.lwjgl.glfw.GLFW.glfwSetKeyCallback;
import static org.lwjgl.glfw.GLFW.glfwSetWindowPos;
import static org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose;
import static org.lwjgl.glfw.GLFW.glfwShowWindow;
import static org.lwjgl.glfw.GLFW.glfwSwapBuffers;
import static org.lwjgl.glfw.GLFW.glfwSwapInterval;
import static org.lwjgl.glfw.GLFW.glfwTerminate;
import static org.lwjgl.glfw.GLFW.glfwWindowHint;
import static org.lwjgl.glfw.GLFW.glfwWindowShouldClose;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

import io.github.chiraagchakravarthy.lwjgl_vectorized_text.TextRenderer;
import io.github.chiraagchakravarthy.lwjgl_vectorized_text.VectorFont;
import org.joml.Vector2f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.Version;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

import java.awt.*;
import java.nio.DoubleBuffer;
import java.nio.IntBuffer;

public class MainClass {

  private long window;
  private IMultiPartialDiskRenderer renderer;

  private ConcurrentHierarchicalFolderSizeCalculator calculator;
  private SunburstFolderSizeVisualizer visualizer;
  private String currentPath = "";

  private TextRenderer textRenderer;

  public void run() {
    System.out.println("Hello LWJGL " + Version.getVersion() + "!");

    init();
    loop();

    // Free the window callbacks and destroy the window
    glfwFreeCallbacks(window);
    glfwDestroyWindow(window);

    // Terminate GLFW and free the error callback
    glfwTerminate();
    glfwSetErrorCallback(null).free();
  }

  private void init() {
    // Setup an error callback
    GLFWErrorCallback.createPrint(System.err).set();

    // Initialize GLFW
    if (!glfwInit()) {
      throw new IllegalStateException("Unable to initialize GLFW");
    }

    // Configure GLFW
    glfwDefaultWindowHints();
    glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
    glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);

    // Create the window
    window = glfwCreateWindow(800, 600, "Partial Disk Renderer", NULL, NULL);
    if (window == NULL) {
      throw new RuntimeException("Failed to create the GLFW window");
    }

    // Setup a key callback
    glfwSetKeyCallback(window, (windowp, key, scancode, action, mods) -> {
      if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
        glfwSetWindowShouldClose(windowp, true);
      }
    });

    // Get the thread stack and push a new frame
    try (MemoryStack stack = stackPush()) {
      IntBuffer pWidth = stack.mallocInt(1);
      IntBuffer pHeight = stack.mallocInt(1);

      // Get the window size passed to glfwCreateWindow
      glfwGetWindowSize(window, pWidth, pHeight);

      // Get the resolution of the primary monitor
      GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());

      // Center the window
      glfwSetWindowPos(window, (vidmode.width() - pWidth.get(0)) / 2, (vidmode.height() - pHeight.get(0)) / 2);
    } // the stack frame is popped automatically

    // Make the OpenGL context current
    glfwMakeContextCurrent(window);
    // Enable v-sync
    glfwSwapInterval(1);

    // Make the window visible
    glfwShowWindow(window);
  }

  private void loop() {
    Long currentSize;
    // This line is critical for LWJGL's interoperation with GLFW's
    // OpenGL context, or any context that is managed externally.
    // LWJGL detects the context that is current in the current thread,
    // creates the GLCapabilities instance and makes the OpenGL
    // bindings available for use.
    GL.createCapabilities();

    // Initialize our renderers
    if (renderer == null) {
      VectorFont font = new VectorFont("/font/arial.ttf");
      textRenderer = new TextRenderer(font);
      renderer = new MultiPartialDiskRenderer();
      renderer.init(32);  // 32 slices
      visualizer = new SunburstFolderSizeVisualizer(renderer);
      // test calculate size
      String rootPath = "C:\\"; // Replace with your desired path
      calculator = new ConcurrentHierarchicalFolderSizeCalculator();
      calculator.startCalculation(rootPath);
    }

    // Set the clear color
    glClearColor(1.0f, 1.0f, 1.0f, 1.0f);

    // Run the rendering loop until the user has attempted to close
    // the window or has pressed the ESCAPE key.
    while (!glfwWindowShouldClose(window)) {
      glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); // clear the framebuffer

      //
      FolderInfo rootInfo = calculator.getRootFolderInfo();
      renderer.cleanInstances();
      visualizer.visualize(rootInfo, 0.0f, 0.0f, 0.1f);

      // retrieve the path from the mouse coordinates
      // Update instance data
      renderer.updateInstanceData();
      float[] xy = getOpenGLMousePosition(window);
      String path = visualizer.findPathFromCoordinate(xy[0], xy[1]);

      openPathIfMousePressed(path);

      // If the path has changed, update the current path and size
      if (path != null && !path.equals(currentPath)) {
        FolderInfo folderInfo = calculator.getFolderInfo(path);
        currentPath = path;
//        currentSize = folderInfo.getSize();
//        System.out.println(
//            "Mouse at: " + xy[0] + ", " + xy[1] + " Path: " + path + " Size: " + formatSize(currentSize));
      }
      // Render the partial disks
      renderer.render();

      textRenderer.drawText2D(currentPath, xy[2], 600-xy[3], 10f,new Vector2f(-1,-1),TextRenderer.TextBoundType.BASELINE, new Vector4f(0,0,0,1));
      textRenderer.render();

      glfwSwapBuffers(window); // swap the color buffers

      // Poll for window events. The key callback above will only be
      // invoked during this call.
      glfwPollEvents();
    }

    // Cleanup
    renderer.cleanup();
    calculator.stop();
  }

  private void openPathIfMousePressed(String path) {
    if (GLFW.glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_1) == GLFW_PRESS) {
      try {
        Desktop.getDesktop().open(new java.io.File(path));
      }
      catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  private String formatSize(long size) {
    if (size >= 1_000_000_000) {
      return String.format("%.2f GB", size / 1_000_000_000.0);
    }
    else if (size >= 1_000_000) {
      return String.format("%.2f MB", size / 1_000_000.0);
    }
    else if (size >= 1_000) {
      return String.format("%.2f KB", size / 1_000.0);
    }
    else {
      return size + " bytes";
    }
  }

  public static float[] getOpenGLMousePosition(long window) {
    DoubleBuffer xBuffer = BufferUtils.createDoubleBuffer(1);
    DoubleBuffer yBuffer = BufferUtils.createDoubleBuffer(1);

    // Get the current mouse position
    GLFW.glfwGetCursorPos(window, xBuffer, yBuffer);

    // Get the mouse coordinates
    double mouseX = xBuffer.get(0);
    double mouseY = yBuffer.get(0);

    // Convert to OpenGL coordinates
    float openglX = (float) (2.0 * mouseX / 800 - 1.0);
    float openglY = (float) (1.0 - 2.0 * mouseY / 600);

    return new float[] {openglX, openglY, (float) mouseX, (float) mouseY};
  }

  public static void main(String[] args) {
    new MainClass().run();
  }
}