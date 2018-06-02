export interface BitBucketConfig {
   username: string;
   password: string;
   project: string;
   server: string;
   url: string;
}
export declare function checkProject(config: BitBucketConfig): Promise<any>
export declare function onRepo(config: BitBucketConfig, repoSlug: string): Promise<any>
export declare function testCallback(config: BitBucketConfig, callback: any): void
