package com.example.project1.fragments

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.project1.R
import com.example.project1.databinding.FragmentMlkitBinding
import com.example.project1.repositories.DataBase
import com.example.project1.repositories.Event
import com.google.ar.core.*
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.NotYetAvailableException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import ru.misis.arcore.common.helpers.CameraPermissionHelper
import ru.misis.arcore.common.helpers.DepthSettings
import ru.misis.arcore.common.helpers.DisplayRotationHelper
import ru.misis.arcore.common.helpers.TapHelper
import ru.misis.arcore.common.samplerender.*
import ru.misis.arcore.common.samplerender.arcore.BackgroundRenderer
import ru.misis.arcore.common.samplerender.arcore.PlaneRenderer
import ru.misis.arcore.common.samplerender.arcore.SpecularCubemapFilter
import timber.log.Timber
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*

class MLKitFragment : Fragment(), SampleRender.Renderer {
    private lateinit var binding: FragmentMlkitBinding

    // Rendering. The Renderers are created here, and initialized when the GL surface is created
    private lateinit var surfaceView: GLSurfaceView
    private var session: Session? = null
    private val CUBEMAP_RESOLUTION = 16
    private val CUBEMAP_NUMBER_OF_IMPORTANCE_SAMPLES = 32
    private val Z_NEAR = 0.1f
    private val Z_FAR = 100f
    private val SEARCHING_PLANE_MESSAGE = "Searching for surfaces..."
    private val WAITING_FOR_TAP_MESSAGE = "Tap on a surface to place an object."

    // Assumed distance from the device camera to the surface on which user will try to place ob
    // This value affects the apparent scale of objects while the tracking method of the
    // Instant Placement point is SCREENSPACE_WITH_APPROXIMATE_DISTANCE.
    // Values in the [0.2, 2.0] meter range are a good choice for most AR experiences. Use lower
    // values for AR experiences where users are expected to place objects on surfaces close to
    // camera. Use larger values for experiences where the user will likely be standing and tryi
    // place an object on the ground or floor in front of them.
    private val APPROXIMATE_DISTANCE_METERS = 2.0f
    private val depthSettings: DepthSettings = DepthSettings()
    private var installRequested = false
    private lateinit var displayRotationHelper: DisplayRotationHelper
    private lateinit var tapHelper: TapHelper
    private var planeRenderer: PlaneRenderer? = null
    private lateinit var backgroundRenderer: BackgroundRenderer
    private var virtualSceneFramebuffer: Framebuffer? = null
    private var hasSetTextureNames = false

    // Point Cloud
    private lateinit var pointCloudVertexBuffer: VertexBuffer
    private var pointCloudMesh: Mesh? = null
    private var pointCloudShader: Shader? = null

    // Keep track of the last point cloud rendered to avoid updating the VBO if point cloud
    // was not changed.  Do this using the timestamp since we can't compare PointCloud objects.
    private var lastPointCloudTimestamp: Long = 0

    // Virtual object (ARCore pawn)
    private var virtualObjectMesh: Mesh? = null
    private var virtualObjectShader: Shader? = null
    private val anchors = ArrayList<Anchor>()

    // Temporary matrix allocated here to reduce number of allocations for each frame.
    private val modelMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val modelViewMatrix = FloatArray(16) // view x model
    private val modelViewProjectionMatrix =
        FloatArray(16) // projection x view x model
    private val recognizer = TextRecognition.getClient()
    private var jobRecognizer: Boolean = true
    private val arArray = mutableMapOf<String,Boolean>()

    private val scope = CoroutineScope(Dispatchers.IO)

    private val observer = androidx.lifecycle.Observer<Event>{
        binding.textView1.text =
            if (it.name.isNullOrEmpty()) ""
            else requireActivity().getString(
                R.string.eventInfo,
                it.date,
                it.startEvent,
                it.endEvent,
                it.name,
                it.responsible
            )
    }

    private var b: Int = 0
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding =
            DataBindingUtil.inflate(inflater, R.layout.fragment_mlkit, container, false)

        DataBase.currentEvent.observeForever(observer)

        binding.back.setOnClickListener {
            findNavController().popBackStack()
        }
        surfaceView = binding.surfaceview
        displayRotationHelper = DisplayRotationHelper(requireContext())

        // Set up touch listener.
        tapHelper = TapHelper(requireContext())
        surfaceView.setOnTouchListener(tapHelper)

        // Set up renderer.
        SampleRender(surfaceView, this, requireActivity().assets)

        installRequested = false

        depthSettings.onCreate(requireContext())

        return binding.root

    }

    override fun onResume() {
        super.onResume()

        // ARCore requires camera permission to operate.
        if (!CameraPermissionHelper.hasCameraPermission(requireActivity())) {
            CameraPermissionHelper.requestCameraPermission(requireActivity())
            return
        }

        if (session == null) {
            try {
                when (ArCoreApk.getInstance().requestInstall(requireActivity(), true)) {
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                        installRequested = true
                        return
                    }
                    ArCoreApk.InstallStatus.INSTALLED -> {
                    }
                    else -> {
                    }
                }

                // Create the session.
                session = Session( /* context= */requireContext())
                Timber.i("Создали сессию")
            } catch (e: Throwable) {
                Timber.e(e, "Что-то пошло не так")
            }
        }

        // Note that order matters - see the note in onPause(), the reverse applies here.
        try {
            configureSession()
            // To record a live camera session for later playback, call
            // `session.startRecording(recorderConfig)` at anytime. To playback a previously recorded AR
            // session instead of using the live camera feed, call
            // `session.setPlaybackDataset(playbackDatasetPath)` before calling `session.resume()`. To
            // learn more about recording and playback, see:
            // https://developers.google.com/ar/develop/java/recording-and-playback
            session!!.resume()
        } catch (e: CameraNotAvailableException) {
            Timber.e("Camera not available. Try restarting the app.")
            session = null
            return
        }

        surfaceView.onResume()
        displayRotationHelper.onResume()
    }


    override fun onPause() {
        super.onPause()
        if (session != null) {
            // Note that the order matters - GLSurfaceView is paused first so that it does not try
            // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
            // still call session.update() and get a SessionPausedException.
            displayRotationHelper.onPause()
            surfaceView.onPause()
            session!!.pause()
        }
    }


    /** Configures the session with feature settings.  */
    private fun configureSession() {
        val config = session!!.config

        // Выключаем свет
        config.lightEstimationMode = Config.LightEstimationMode.DISABLED
        //config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR

        if (session!!.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
            config.depthMode = Config.DepthMode.AUTOMATIC
        } else {
            config.depthMode = Config.DepthMode.DISABLED
        }

        config.instantPlacementMode = Config.InstantPlacementMode.DISABLED

        session!!.configure(config)
    }


    private var visiontext: Text? = null

    override fun onDrawFrame(render: SampleRender?) {
        if (session == null) {
            return
        }

        // Texture names should only be set once on a GL thread unless they change. This is done during
        // onDrawFrame rather than onSurfaceCreated since the session is not guaranteed to have been
        // initialized during the execution of onSurfaceCreated.
        if (!hasSetTextureNames) {
            session!!.setCameraTextureNames(
                intArrayOf(
                    backgroundRenderer.cameraColorTexture.textureId
                )
            )
            hasSetTextureNames = true
        }

        // -- Update per-frame state

        // Notify ARCore session that the view size changed so that the perspective matrix and
        // the video background can be properly adjusted.
        displayRotationHelper.updateSessionIfNeeded(session!!)

        // Obtain the current frame from ARSession. When the configuration is set to
        // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
        // camera framerate.
        val frame: Frame
        frame = try {
            session!!.update()
        } catch (e: CameraNotAvailableException) {
            Timber.e(e, "Camera not available during onDrawFrame")
            return
        }
        val camera = frame.camera

        // Update BackgroundRenderer state to match the depth settings.
        try {
            backgroundRenderer.setUseDepthVisualization(
                render, depthSettings.depthColorVisualizationEnabled()
            )
            backgroundRenderer.setUseOcclusion(render, depthSettings.useDepthForOcclusion())
        } catch (e: IOException) {
            Timber.e(e, "Failed to read a required asset file")
            return
        }

        // BackgroundRenderer.updateDisplayGeometry must be called every frame to update the coordinates
        // used to draw the background camera image.
        backgroundRenderer.updateDisplayGeometry(frame)

        if (camera.trackingState == TrackingState.TRACKING
            && (depthSettings.useDepthForOcclusion()
                    || depthSettings.depthColorVisualizationEnabled())
        ) {
            try {
                val depthImage = frame.acquireDepthImage()
                backgroundRenderer.updateCameraDepthTexture(depthImage)
            } catch (e: NotYetAvailableException) {
                // This normally means that depth data is not available yet. This is normal so we will not
                // spam the logcat with this.
            }
        }

        if (jobRecognizer) {
            jobRecognizer = false
            try {
                val image = frame.acquireCameraImage()
                image?.let { imageInput ->
                    try {
                        val inputImage = InputImage.fromMediaImage(imageInput, 90)
                        recognizer.process(inputImage)
                            .addOnSuccessListener {
                                println("visionText = ${it ?: "null"}")
                                scope.launch {
                                    DataBase.getEventFromServer(it.text)
                                }
                            }
                            .addOnFailureListener { error ->
                                Timber.e(error, "Failed to process the image")
                                error.printStackTrace()
                            }.addOnCompleteListener {
                                println("Закрываем")
                                imageInput.close()
                                image.close()
                                jobRecognizer = true
                            }
                    } catch (e: IOException) {
                        Timber.e(e,"Failed to load the image")
                        e.printStackTrace()
                    }
                }

            } catch (e: Throwable) {
                Timber.i(e, "FAIL")
            }
        }

        noTap(frame, camera)

        // -- Draw background
        if (frame.timestamp != 0L) {
            // Suppress rendering if the camera did not produce the first frame yet. This is to avoid
            // drawing possible leftover data from previous sessions if the texture is reused.
            backgroundRenderer.drawBackground(render)
        }

        // If not tracking, don't draw 3D objects.
        if (camera.trackingState == TrackingState.PAUSED) {
            return
        }

        // -- Draw non-occluded virtual objects (planes, point cloud)

        // Get projection matrix.
        camera.getProjectionMatrix(
            projectionMatrix,
            0,
            Z_NEAR,
            Z_FAR
        )

        // Get camera matrix and draw.
        camera.getViewMatrix(viewMatrix, 0)

        // Visualize tracked points.
        // Use try-with-resources to automatically release the point cloud.
        frame.acquirePointCloud().use { pointCloud ->
            if (pointCloud.timestamp > lastPointCloudTimestamp) {
                pointCloudVertexBuffer.set(pointCloud.points)
                lastPointCloudTimestamp = pointCloud.timestamp
            }
            Matrix.multiplyMM(
                modelViewProjectionMatrix,
                0,
                projectionMatrix,
                0,
                viewMatrix,
                0
            )
            pointCloudShader!!.setMat4("u_ModelViewProjection", modelViewProjectionMatrix)
            render!!.draw(pointCloudMesh, pointCloudShader)
        }

        // Visualize planes.
        planeRenderer!!.drawPlanes(
            render,
            session!!.getAllTrackables(Plane::class.java),
            camera.displayOrientedPose,
            projectionMatrix
        )
        // -- Draw occluded virtual objects

        // Update lighting parameters in the shader
        //updateLightEstimation(frame.lightEstimate, viewMatrix)

        // Visualize anchors created by touch.
        render!!.clear(virtualSceneFramebuffer, 0f, 0f, 0f, 0f)
        for (anchor in anchors) {
            if (anchor.trackingState != TrackingState.TRACKING) {
                continue
            }

            // Get the current pose of an Anchor in world space. The Anchor pose is updated
            // during calls to session.update() as ARCore refines its estimate of the world.
            anchor.pose.toMatrix(modelMatrix, 0)

            // Calculate model/view/projection matrices
            Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0)
            Matrix.multiplyMM(
                modelViewProjectionMatrix,
                0,
                projectionMatrix,
                0,
                modelViewMatrix,
                0
            )

            // Update shader properties and draw
            virtualObjectShader!!.setMat4("u_ModelView", modelViewMatrix)
            virtualObjectShader!!.setMat4("u_ModelViewProjection", modelViewProjectionMatrix)
            render.draw(virtualObjectMesh, virtualObjectShader, virtualSceneFramebuffer)
        }

        // Compose the virtual scene with the background.
        backgroundRenderer.drawVirtualScene(
            render,
            virtualSceneFramebuffer,
            Z_NEAR,
            Z_FAR
        )
    }

    private fun noTap(
        frame: Frame,
        camera: Camera
    ) {
        val event = DataBase.currentEvent.value
        Timber.i("Event = $event")
        Timber.i("count = $b")

        val numberClassroom = event?.numberClassRoom ?: return



        val tap = tapHelper.poll()
        if (tap != null)
            println("tap =  ${tap.x} and ${tap.y}")

        if (arArray[numberClassroom] != true && camera.trackingState == TrackingState.TRACKING) {
            val hitResultList: List<HitResult> =
                frame.hitTest(

                    465.0f, 903.5f//,
                    //APPROXIMATE_DISTANCE_METERS
                )

            println("hitResultList = ${hitResultList.size}")

            for (hit in hitResultList) {
                // If any plane, Oriented Point, or Instant Placement Point was hit, create an anchor.
                val trackable = hit.trackable
                // If a plane was hit, check that it was hit inside the plane polygon.
                if ((trackable is Plane
                            && trackable.isPoseInPolygon(hit.hitPose)
                            && PlaneRenderer.calculateDistanceToPlane(
                        hit.hitPose,
                        camera.pose
                    ) > 0)
                    || (trackable is Point
                            && trackable.orientationMode
                            == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL)
                    || trackable is InstantPlacementPoint
                ) {
                    // Cap the number of objects created. This avoids overloading both the
                    // rendering system and ARCore.
                    if (anchors.size >= 20) {
                        anchors[0].detach()
                        anchors.removeAt(0)
                    }

                    // Adding an Anchor tells ARCore that it should track this position in
                    // space. This anchor is created on the Plane to place the 3D model
                    // in the correct position relative both to the world and to the plane.
                    anchors.add(hit.createAnchor())
                    arArray[numberClassroom] = true
                    // Hits are sorted by depth. Consider only closest hit on a plane, Oriented Point, or
                    // Instant Placement Point.
                    break
                }
            }

        }
    }

    override fun onSurfaceChanged(render: SampleRender?, width: Int, height: Int) {
        displayRotationHelper.onSurfaceChanged(width, height)
        virtualSceneFramebuffer?.resize(width, height)
    }

    override fun onSurfaceCreated(render: SampleRender?) {
        // Prepare the rendering objects. This involves reading shaders and 3D model files, so may throw
        // an IOException.
        try {
            planeRenderer = PlaneRenderer(render)
            backgroundRenderer = BackgroundRenderer(render)
            virtualSceneFramebuffer = Framebuffer(render,  /*width=*/1,  /*height=*/1)
            val cubemapFilter = SpecularCubemapFilter(
                render,
                CUBEMAP_RESOLUTION,
                CUBEMAP_NUMBER_OF_IMPORTANCE_SAMPLES
            )
            // Load DFG lookup table for environmental lighting
            val dfgTexture = Texture(
                render,
                Texture.Target.TEXTURE_2D,
                Texture.WrapMode.CLAMP_TO_EDGE,  /*useMipmaps=*/
                false
            )
            // The dfg.raw file is a raw half-float texture with two channels.
            val dfgResolution = 64
            val dfgChannels = 2
            val halfFloatSize = 2
            val buffer =
                ByteBuffer.allocateDirect(dfgResolution * dfgResolution * dfgChannels * halfFloatSize)
            requireActivity().assets.open("models/dfg.raw")
                .use { `is` -> `is`.read(buffer.array()) }
            // SampleRender abstraction leaks here.
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, dfgTexture.textureId)
            Timber.e("Failed to bind DFG texture glBindTexture")
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D,  /*level=*/
                0,
                GLES30.GL_RG16F,  /*width=*/
                dfgResolution,  /*height=*/
                dfgResolution,  /*border=*/
                0,
                GLES30.GL_RG,
                GLES30.GL_HALF_FLOAT,
                buffer
            )
            Timber.e("Failed to populate DFG texture glTexImage2D")

            // Point cloud
            pointCloudShader = Shader.createFromAssets(
                render, "shaders/point_cloud.vert", "shaders/point_cloud.frag",  /*defines=*/null
            )
                .setVec4(
                    "u_Color", floatArrayOf(31.0f / 255.0f, 188.0f / 255.0f, 210.0f / 255.0f, 1.0f)
                )
                .setFloat("u_PointSize", 5.0f)
            // four entries per vertex: X, Y, Z, confidence
            pointCloudVertexBuffer =
                VertexBuffer(render,  /*numberOfEntriesPerVertex=*/4,  /*entries=*/null)
            val pointCloudVertexBuffers: Array<VertexBuffer> =
                arrayOf<VertexBuffer>(pointCloudVertexBuffer)
            pointCloudMesh = Mesh(
                render, Mesh.PrimitiveMode.POINTS,  /*indexBuffer=*/null, pointCloudVertexBuffers
            )

            // Virtual object to render (ARCore pawn)
            val virtualObjectAlbedoTexture: Texture = Texture.createFromAsset(
                render,
                "models/pawn_albedo.png",
                Texture.WrapMode.CLAMP_TO_EDGE,
                Texture.ColorFormat.SRGB
            )
            val virtualObjectPbrTexture: Texture = Texture.createFromAsset(
                render,
                "models/pawn_roughness_metallic_ao.png",
                Texture.WrapMode.CLAMP_TO_EDGE,
                Texture.ColorFormat.LINEAR
            )
            virtualObjectMesh = Mesh.createFromAsset(render, "models/pawn.obj")
            virtualObjectShader = Shader.createFromAssets(
                render,
                "shaders/environmental_hdr.vert",
                "shaders/environmental_hdr.frag",  /*defines=*/
                object : HashMap<String?, String?>() {
                    init {
                        put(
                            "NUMBER_OF_MIPMAP_LEVELS",
                            Integer.toString(cubemapFilter.getNumberOfMipmapLevels())
                        )
                    }
                })
                .setTexture("u_AlbedoTexture", virtualObjectAlbedoTexture)
                .setTexture("u_RoughnessMetallicAmbientOcclusionTexture", virtualObjectPbrTexture)
                .setTexture("u_Cubemap", cubemapFilter.getFilteredCubemapTexture())
                .setTexture("u_DfgTexture", dfgTexture)
        } catch (e: IOException) {
            Timber.e(e, "Failed to read a required asset file")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (!CameraPermissionHelper.hasCameraPermission(requireActivity())) {
            Toast.makeText(
                requireContext(),
                "Camera permission is needed to run this application",
                Toast.LENGTH_LONG
            )
                .show()
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(requireActivity())) {
                // Permission denied with checking "Do not ask again".
                CameraPermissionHelper.launchPermissionSettings(requireActivity())
            }
            requireActivity().finish()
        }
    }

    override fun onDestroy() {
        if (session != null) {
            // Explicitly close ARCore Session to release native resources.
            // Review the API reference for important considerations before calling close() in apps with
            // more complicated lifecycle requirements:
            // https://developers.google.com/ar/reference/java/arcore/reference/com/google/ar/core/Session#close()
            session!!.close()
            session = null
        }
        DataBase.currentEvent.removeObserver(observer)
        scope.cancel()
        super.onDestroy()
    }
}