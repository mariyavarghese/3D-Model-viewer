# 3D Model Viewer — Multi-Model Interactive Android App

An Android application built with **Kotlin** and **Google Filament** capable of rendering multiple 3D models (`.glb`) simultaneously on a single canvas. Each model resides in an independent container supporting smooth dragging, pinch-to-resize, 3D orbit/zoom interaction, and dynamic 3D-to-2D projected part labels.

## Key Features

1. **Single-Activity Canvas Architecture**
   - Seamless multi-model viewport in a single Activity without Fragment overhead.
   - Dynamic model loader with BottomSheet picker displaying all 5 bundled models.
   - Supports 5+ models loaded concurrently with smooth 30–60 FPS rendering.

2. **Isolated Dual Gesture System (Section 1.7 Guarantee)**
   - **Normal Mode (Default)**:
     - **1-Finger Drag**: Translates the container across the canvas using hardware-accelerated view properties (`x`, `y`).
     - **2-Finger Pinch**: Resizes the container via `ScaleGestureDetector`, with Filament dynamically adjusting viewport and camera projection.
     - *Zero 3D rotation/zoom occurs in this mode.*
   - **Interaction Mode (Active Toggle)**:
     - **1-Finger Drag**: Orbits/rotates the 3D model in 3D space.
     - **2-Finger Pinch**: Zooms the 3D camera in and out.
     - *Container position (`x`, `y`) and dimensions (`width`, `height`) remain strictly stationary.*
   - **Visual Feedback**: The container border transitions to a glowing Cyan accent (`#38BDF8`) when in Interaction Mode, and button badges clearly reflect active states.

3. **3D-to-2D Part Labels with Real-Time Projection (Section 2)**
   - Custom glTF parser (`GltfParser.kt`) extracts node names and labels from `extras.prop` in GLB binary JSON headers.
   - Extracts world positions from Filament's `TransformManager` composite hierarchy (accounting for root unit-cube transforms).
   - Projects 3D $(X, Y, Z)$ coordinates every frame through Camera View and Projection matrices:
     $$\mathbf{P}_{\text{clip}} = (\mathbf{P}_{\text{proj}} \times \mathbf{V}_{\text{view}}) \cdot \mathbf{P}_{\text{world}}$$
   - High-performance `LabelOverlayView` renders stylish blueprint pin dots, dynamic connector lines, and glassmorphism badges with zero runtime object allocations in the draw loop.
   - Automatically culls labels behind the camera ($W_{\text{clip}} \le 0$) or outside the view frustum.

4. **Always-Visible Model Container Controls (Section 1.6)**
   - **Interaction Toggle**: Toggles between Normal container gestures and 3D camera orbit/zoom.
   - **Label Toggle**: Toggles 3D-anchored anatomy labels ON/OFF (hidden by default).
   - **Close Button**: Removes the container, detaches surface, and releases native Filament resources.

---

##  3D Library Choice: Google Filament

### Why Filament?
- **Lightweight & High-Performance C++ Core**: Built specifically for mobile by Google with a physically based rendering (PBR) pipeline.
- **Low Memory Footprint**: Highly efficient entity-component system (ECS) that minimizes Java heap allocations and garbage collection pauses.
- **Direct GLB / glTF 2.0 Support**: `gltfio` parses standard binary GLBs and exposes entity hierarchies and transform nodes directly.
- **TextureView Integration**: Multiple `TextureView` instances can render independently inside custom `ViewGroup`s without OpenGL surface collisions.

---

##  Performance Optimizations for Low-End Devices (2–3 GB RAM)

1. **Zero-Allocation Render Loop**:
   - Pre-allocated float arrays (`viewMatrix`, `projMatrix`, `viewProjMatrix`, `clipPosBuffer`, `worldPosBuffer`, `outScreenBuffer`) and `Paint` objects prevent garbage collection churn during 60 FPS Choreographer callbacks.
2. **Hardware-Accelerated Translation**:
   - Moving containers updates `view.x` and `view.y` directly, which leverages Android's RenderNode transform matrices without triggering costly layout passes across parent containers.
3. **Smart Asset Caching**:
   - GLB models are read into direct `ByteBuffer`s and cached in memory, eliminating redundant I/O operations when reloading models.
4. **Lifecycle & Native Resource Cleanup**:
   - Choreographer callbacks pause during `onPause()` to eliminate background CPU/GPU drain.
   - Closing a container releases swap chains, materials, and Filament entities cleanly.
5. **Translucent Viewport Optimization**:
   - Blending mode set to `TRANSLUCENT` with optimized light intensities, avoiding unnecessary heavy post-processing passes on weak GPUs.

---

## 📐 Architecture Overview

```
com.mariya.modelviewer/
├── MainActivity.kt        # Canvas manager, UI controls, model picker, and Choreographer loop
├── ModelContainer.kt      # Draggable/resizable card hosting TextureView, controls, and gesture dispatcher
├── LabelOverlayView.kt    # Zero-allocation 2D canvas overlay drawing pins, connector lines, and text badges
├── GltfParser.kt          # Little-endian binary GLB parser extracting extras.prop node annotations
├── MatrixUtils.kt         # Pure Kotlin zero-stub 4x4 matrix multiplication and 3D screen projection
└── ModelItem.kt           # Metadata for the 5 bundled GLB models (Bulb, Fiagena, Lungs, Microscope, Solar System)
```

