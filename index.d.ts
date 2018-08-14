export interface Policy {
   username: string;
   password: string;
   project: string;
   server: string;
   url: string;
}
export declare function converge(policy: Policy): Promise<any>
export declare function onRepo(policy: Policy, repoSlug: string): Promise<any>
