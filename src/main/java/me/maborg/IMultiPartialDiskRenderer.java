package me.maborg;

public interface IMultiPartialDiskRenderer {
  void init(int slices);

  void add(
      float centerX, float centerY, float startAngle, float sweepAngle, float innerRadius, float outerRadius,
      float r, float g, float b, float a);

  void cleanInstances();

  void updateInstanceData();

  void render();

  void cleanup();
}
