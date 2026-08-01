// Module catalog resources under src/main/resources/modules/*/files include dotfiles
// (.gitignore) meant to be copied verbatim into generated projects. Ant's DirectoryScanner
// silently drops those from every Copy-family task (processResources included) unless removed
// here, before the file-tree snapshotter walks the resources.
org.apache.tools.ant.DirectoryScanner.removeDefaultExclude("**/.gitignore")
org.apache.tools.ant.DirectoryScanner.removeDefaultExclude("**/.gitattributes")

rootProject.name = "jloom"
