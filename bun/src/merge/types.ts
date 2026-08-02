export interface AddDependencyOp {
  type: "org.openrewrite.gradle.AddDependency";
  groupId: string;
  artifactId: string;
  version?: string;
  configuration: string;
}

export interface MergeYamlOp {
  type: "org.openrewrite.yaml.MergeYaml";
  key: string;
  yaml: string;
  filePattern: string;
}

export interface ChangePropertyKeyOp {
  type: "org.openrewrite.yaml.ChangePropertyKey";
  oldPropertyKey: string;
  newPropertyKey: string;
  filePattern: string;
}

export interface CreateTextFileOp {
  type: "org.openrewrite.text.CreateTextFile";
  relativeFileName: string;
  fileContents: string;
  overwriteExisting: boolean;
}

export type MergeOperation = AddDependencyOp | MergeYamlOp | ChangePropertyKeyOp | CreateTextFileOp;
