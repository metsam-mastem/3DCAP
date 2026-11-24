package studio.threedcap.core

enum class ReconstructionProfile { DRAFT, BALANCED, HIGH }
enum class ColmapStage {
    VALIDATE_INPUT, FEATURE_EXTRACTION, MATCHING, SPARSE_MAPPING, UNDISTORT, DENSE_STEREO, FUSION
}

data class ColmapCommandStage(
    val stage: ColmapStage,
    val command: String?,
    val arguments: List<String>
)

data class ColmapWorkflowPlan(
    val workspacePath: String,
    val executablePath: String,
    val profile: ReconstructionProfile,
    val stages: List<ColmapCommandStage>
) {
    companion object {
        fun create(
            workspacePath: String,
            executablePath: String,
            profile: ReconstructionProfile
        ): ColmapWorkflowPlan {
            require(workspacePath.isNotBlank() && !workspacePath.contains('\n') && !workspacePath.contains('\r')) {
                "Workspace path is required and must be a single line"
            }
            require(executablePath.isNotBlank() && !executablePath.contains('\n') && !executablePath.contains('\r')) {
                "COLMAP executable path is required and must be a single line"
            }
            val trimmedWorkspace = workspacePath.trim()
            val isWindowsDriveRoot = Regex("^[A-Za-z]:[\\\\/]*$").matches(trimmedWorkspace)
            val workspace = if (isWindowsDriveRoot) {
                trimmedWorkspace.take(2) + "\\"
            } else {
                trimmedWorkspace.trimEnd('/', '\\').ifEmpty { trimmedWorkspace }
            }
            fun childPath(parent: String, child: String): String {
                val base = parent.trimEnd('/', '\\')
                val separator = if ('\\' in parent) "\\" else "/"
                return if (base.isEmpty()) "$separator$child" else "$base$separator$child"
            }
            val images = childPath(workspace, "images")
            val database = childPath(workspace, "database.db")
            val sparse = childPath(workspace, "sparse")
            val dense = childPath(workspace, "dense")
            val maxSize = when (profile) {
                ReconstructionProfile.DRAFT -> "1200"
                ReconstructionProfile.BALANCED -> "2000"
                ReconstructionProfile.HIGH -> "3200"
            }
            val stages = listOf(
                ColmapCommandStage(ColmapStage.VALIDATE_INPUT, null, emptyList()),
                ColmapCommandStage(ColmapStage.FEATURE_EXTRACTION, "feature_extractor",
                    listOf("--database_path", database, "--image_path", images)),
                ColmapCommandStage(ColmapStage.MATCHING, "exhaustive_matcher",
                    listOf("--database_path", database)),
                ColmapCommandStage(ColmapStage.SPARSE_MAPPING, "mapper",
                    listOf("--database_path", database, "--image_path", images, "--output_path", sparse)),
                ColmapCommandStage(ColmapStage.UNDISTORT, "image_undistorter",
                    listOf("--image_path", images, "--input_path", childPath(sparse, "0"), "--output_path", dense,
                        "--output_type", "COLMAP", "--max_image_size", maxSize)),
                ColmapCommandStage(ColmapStage.DENSE_STEREO, "patch_match_stereo",
                    listOf("--workspace_path", dense, "--workspace_format", "COLMAP",
                        "--PatchMatchStereo.geom_consistency", "true")),
                ColmapCommandStage(ColmapStage.FUSION, "stereo_fusion",
                    listOf("--workspace_path", dense, "--workspace_format", "COLMAP",
                        "--input_type", "geometric", "--output_path", childPath(dense, "fused.ply")))
            )
            return ColmapWorkflowPlan(workspace, executablePath, profile, stages)
        }

        fun hasSparseModel(fileNames: Set<String>): Boolean =
            setOf("cameras.bin", "images.bin", "points3D.bin").all { it in fileNames }

        fun hasDensePointCloud(fileNames: Set<String>): Boolean = "fused.ply" in fileNames
    }
}
