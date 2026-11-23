package studio.threedcap.core

import org.junit.Assert.*
import org.junit.Test

class ColmapWorkflowPlanTest {
    @Test fun createsSparseThenDenseWorkflowWithExplicitInputPaths() {
        val plan = ColmapWorkflowPlan.create("C:/3DCAP/session", "C:/Tools/COLMAP.bat", ReconstructionProfile.DRAFT)
        assertEquals(listOf(
            ColmapStage.VALIDATE_INPUT,
            ColmapStage.FEATURE_EXTRACTION,
            ColmapStage.MATCHING,
            ColmapStage.SPARSE_MAPPING,
            ColmapStage.UNDISTORT,
            ColmapStage.DENSE_STEREO,
            ColmapStage.FUSION
        ), plan.stages.map { it.stage })
        assertTrue(plan.stages.first { it.stage == ColmapStage.FEATURE_EXTRACTION }.arguments.contains("--image_path"))
        assertTrue(plan.stages.first { it.stage == ColmapStage.FUSION }.arguments.contains("--output_path"))
    }

    @Test fun draftProfileUsesReducedDenseImageSize() {
        val plan = ColmapWorkflowPlan.create("C:/dataset", "colmap", ReconstructionProfile.DRAFT)
        val undistort = plan.stages.first { it.stage == ColmapStage.UNDISTORT }
        assertEquals("1200", undistort.arguments[undistort.arguments.indexOf("--max_image_size") + 1])
    }

    @Test fun rejectsBlankAndUnsafeWorkspaceInputs() {
        assertThrows(IllegalArgumentException::class.java) {
            ColmapWorkflowPlan.create("", "colmap", ReconstructionProfile.DRAFT)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ColmapWorkflowPlan.create("C:/dataset\n--evil", "colmap", ReconstructionProfile.DRAFT)
        }
    }

    @Test fun readinessRequiresSparseModelAndDensePointCloudArtifacts() {
        assertFalse(ColmapWorkflowPlan.hasSparseModel(emptySet()))
        assertTrue(ColmapWorkflowPlan.hasSparseModel(setOf("cameras.bin", "images.bin", "points3D.bin")))
        assertFalse(ColmapWorkflowPlan.hasDensePointCloud(setOf("fused.ply.tmp")))
        assertTrue(ColmapWorkflowPlan.hasDensePointCloud(setOf("fused.ply")))
    }
}
