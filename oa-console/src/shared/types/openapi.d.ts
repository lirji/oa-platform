export type paths = {
    readonly "/api/v1/admin-biz/assets": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["assets"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/admin-biz/assets/{id}/return": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["giveBack"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/admin-biz/assets/claim": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["claim"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/admin-biz/rooms": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["rooms"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/admin-biz/rooms/{roomId}/bookings": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["bookings"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/admin-biz/rooms/bookings": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["book"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/admin-biz/rooms/bookings/{id}/cancel": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["cancelBooking"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/admin-biz/supplies": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["supplies"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/admin-biz/supplies/requests": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["requestSupply"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/admin-biz/vehicles": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["vehicles"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/admin-biz/vehicles/bookings": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["bookVehicle"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/admin-biz/visitors": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["myVisitors"];
        readonly put?: never;
        readonly post: operations["invite"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/admin-biz/visitors/{id}/check-in": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["checkIn"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/admin-biz/visitors/{id}/check-out": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["checkOut"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/attendance/admin/stats": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["stats"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/attendance/daily-compute": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["computeDaily"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/attendance/me": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["myDay"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/attendance/punch": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["punch"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/doc/kb": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["listKb"];
        readonly put?: never;
        readonly post: operations["createKb"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/doc/kb/{id}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["readKb"];
        readonly put: operations["updateKb"];
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/doc/kb/{id}/explain": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["explain_1"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/doc/kb/authorizer": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["authorizer"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/doc/kb/share": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["share"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/doc/official": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["search_1"];
        readonly put?: never;
        readonly post: operations["draft"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/doc/official/{id}/archive": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["archive"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/doc/official/{id}/issue": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["issue"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/flow/admin/status": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["status"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/flow/docs": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["submit_1"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/flow/docs/mine": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["mine"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/flow/docs/types": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["types_1"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/flow/leave": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["submit"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/flow/leave/{requestNo}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["get_1"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/flow/leave/balances": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["myBalances"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/flow/leave/balances/grant": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["grant_1"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/flow/leave/types": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["types"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/flow/todos": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["myTodos"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/flow/todos/{taskId}/complete": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["complete"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/flow/todos/count": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["count_1"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/flow/todos/mine": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["myApplications"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/iam/abac/conditions": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["list_3"];
        readonly put?: never;
        readonly post: operations["create_4"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/iam/abac/conditions/{id}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put: operations["update_4"];
        readonly post?: never;
        readonly delete: operations["delete_1"];
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/iam/abac/conditions/{id}/enabled": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["enabled_2"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/iam/abac/validate": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["validate"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/iam/admin/bench": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["bench"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/iam/admin/cache-stats": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["cacheStats_1"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/iam/admin/explain": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["explain"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/iam/admin/preview": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["preview"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/iam/admin/reclaim-expired": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["reclaim"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/iam/admin/why": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["why"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/iam/delegations": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["myDelegations"];
        readonly put?: never;
        readonly post: operations["delegate"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/iam/delegations/{id}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post?: never;
        readonly delete: operations["revokeDelegation"];
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/iam/elevation-requests": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["elevationRequests"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/iam/elevation-requests/{id}/approve": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["approveElevation"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/iam/elevation-requests/{id}/reject": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["rejectElevation"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/iam/elevation-requests/mine": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["myElevationRequests"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/iam/elevations": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["elevate"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/iam/elevations/mine": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["myElevations"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/iam/grants": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["list_2"];
        readonly put?: never;
        readonly post: operations["grant"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/iam/grants/{grantId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post?: never;
        readonly delete: operations["revoke"];
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/iam/groups": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["list_1"];
        readonly put?: never;
        readonly post: operations["create_3"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/iam/groups/{id}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put: operations["update_3"];
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/iam/groups/{id}/enabled": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["enabled_1"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/iam/groups/{id}/members": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["members"];
        readonly put?: never;
        readonly post: operations["addMembers"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/iam/groups/{id}/members/{userId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post?: never;
        readonly delete: operations["removeMember"];
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/iam/permissions/catalog": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["catalog"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/iam/role-admin": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["list"];
        readonly put?: never;
        readonly post: operations["create_2"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/iam/role-admin/{id}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["detail"];
        readonly put: operations["update_2"];
        readonly post?: never;
        readonly delete: operations["delete"];
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/iam/role-admin/{id}/copy": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["copy"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/iam/role-admin/{id}/enabled": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["enabled"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/iam/role-admin/{id}/inheritance": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put: operations["inheritance"];
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/iam/role-admin/{id}/permissions": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put: operations["permissions"];
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/iam/roles": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["roles"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/iam/roles/mine": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["myRoles"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/me/permissions": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["myPermissions"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/org/directory": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["search"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/org/directory/count": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["count"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/org/directory/delta": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["delta"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/org/directory/page": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["page"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/org/employees": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["create_1"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/org/employees/{employeeId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put: operations["update_1"];
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/org/employees/{employeeId}/assignments": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["addAssignment"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/org/employees/{employeeId}/leave": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["leave"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/org/employees/{employeeId}/reporting-line": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put: operations["setReportingLine"];
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/org/employees/{employeeId}/transfer": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["transfer"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/org/employees/assignments/{assignmentId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post?: never;
        readonly delete: operations["closeAssignment"];
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/org/employees/by-user/{userId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["byUser"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/org/employees/by-user/{userId}/as-of": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["asOf"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/org/employees/by-user/{userId}/assignments": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["assignments"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/org/employees/by-user/{userId}/manager-chain": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["managerChain"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/org/units": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["create"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/org/units/{orgId}": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["get"];
        readonly put: operations["update"];
        readonly post?: never;
        readonly delete: operations["dissolve"];
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/org/units/{orgId}/ancestors": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["ancestors"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/org/units/{orgId}/descendants": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["descendants"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/org/units/{orgId}/parent": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put: operations["move"];
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/org/units/cache-stats": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["cacheStats"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/org/units/consistency": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["consistency"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/org/units/path-prefixes": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["pathPrefixes"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/org/units/tree": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["tree"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/report/approval-efficiency": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["approval"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/report/attendance": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["attendance"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/report/audit": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["auditQuery"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/report/audit/flush": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get?: never;
        readonly put?: never;
        readonly post: operations["auditFlush"];
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/report/audit/stats": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["auditStats"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/report/headcount": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["headcount"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/report/overview": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["overview"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
    readonly "/api/v1/system/ping": {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly get: operations["ping"];
        readonly put?: never;
        readonly post?: never;
        readonly delete?: never;
        readonly options?: never;
        readonly head?: never;
        readonly patch?: never;
        readonly trace?: never;
    };
};
export type webhooks = Record<string, never>;
export type components = {
    schemas: {
        readonly AbacCondition: {
            readonly description?: string;
            readonly enabled?: boolean;
            readonly expression: string;
            /** Format: int64 */
            readonly permissionId: number;
            /** Format: int64 */
            readonly roleId: number;
        };
        readonly AddAssignment: {
            readonly asLeader?: boolean;
            readonly assignmentType: string;
            /** Format: int64 */
            readonly orgUnitId: number;
            /** Format: int64 */
            readonly positionId?: number;
            /** Format: date */
            readonly validFrom?: string;
        };
        readonly AddGroupMembers: {
            readonly userIds: readonly string[];
            /** Format: date-time */
            readonly validFrom?: string;
            /** Format: date-time */
            readonly validTo?: string;
        };
        readonly AssetView: {
            readonly assetNo?: string;
            readonly category?: string;
            readonly holderId?: string;
            /** Format: int64 */
            readonly id?: number;
            readonly name?: string;
            /** Format: int64 */
            readonly orgId?: number;
            readonly status?: string;
        };
        readonly AssignmentView: {
            readonly assignmentType?: string;
            /** Format: int64 */
            readonly employeeId?: number;
            /** Format: int64 */
            readonly id?: number;
            readonly leader?: boolean;
            readonly orgName?: string;
            readonly orgPath?: string;
            /** Format: int64 */
            readonly orgUnitId?: number;
            /** Format: int64 */
            readonly positionId?: number;
            readonly positionName?: string;
            /** Format: date */
            readonly validFrom?: string;
            /** Format: date */
            readonly validTo?: string;
        };
        readonly BookingView: {
            readonly bookerId?: string;
            readonly bookerName?: string;
            /** Format: date-time */
            readonly endAt?: string;
            /** Format: int64 */
            readonly id?: number;
            /** Format: int64 */
            readonly roomId?: number;
            readonly roomName?: string;
            /** Format: date-time */
            readonly startAt?: string;
            readonly status?: string;
            readonly subject?: string;
        };
        readonly BookRoom: {
            /** Format: int32 */
            readonly attendees?: number;
            /** Format: date-time */
            readonly endAt: string;
            /** Format: int64 */
            readonly roomId: number;
            /** Format: date-time */
            readonly startAt: string;
            readonly subject: string;
        };
        readonly BookVehicle: {
            /** Format: date-time */
            readonly endAt: string;
            readonly purpose?: string;
            /** Format: date-time */
            readonly startAt: string;
            /** Format: int64 */
            readonly vehicleId: number;
        };
        readonly ClaimAsset: {
            /** Format: int64 */
            readonly assetId: number;
            readonly remark?: string;
        };
        readonly CompleteTask: {
            readonly comment?: string;
            readonly onBehalfOf?: string;
            readonly outcome: string;
        };
        readonly CopyRole: {
            readonly code: string;
            readonly name: string;
        };
        readonly CreateEmployee: {
            /** Format: date */
            readonly birthday?: string;
            readonly email?: string;
            readonly employmentType?: string;
            readonly empNo: string;
            readonly enName?: string;
            readonly gender?: string;
            /** Format: date */
            readonly hireDate?: string;
            readonly idCard?: string;
            /** Format: int64 */
            readonly managerEmployeeId?: number;
            readonly mobile?: string;
            readonly name: string;
            /** Format: int64 */
            readonly primaryOrgId: number;
            /** Format: int64 */
            readonly primaryPositionId?: number;
            readonly userId: string;
        };
        readonly CreateGroup: {
            readonly code: string;
            readonly description?: string;
            readonly name: string;
        };
        readonly CreateKbDoc: {
            readonly body?: string;
            readonly fileKey?: string;
            /** Format: int64 */
            readonly folderId?: number;
            readonly summary?: string;
            readonly title: string;
        };
        readonly CreateOrg: {
            readonly code: string;
            readonly costCenter?: string;
            readonly leaderUserId?: string;
            readonly name: string;
            /** Format: int64 */
            readonly parentId?: number;
            readonly remark?: string;
            readonly shortName?: string;
            /** Format: int32 */
            readonly sortOrder?: number;
            readonly type: string;
        };
        readonly CreateRole: {
            readonly code: string;
            readonly defaultScope: string;
            readonly inheritedRoleIds?: readonly number[];
            readonly name: string;
            readonly permissionIds?: readonly number[];
            readonly remark?: string;
        };
        readonly Delegate: {
            readonly delegateeUserId: string;
            readonly processKeys?: readonly string[];
            readonly reason?: string;
            readonly roleIds?: readonly number[];
            readonly scope?: string;
            /** Format: date-time */
            readonly validFrom?: string;
            /** Format: date-time */
            readonly validTo: string;
        };
        readonly Delegation: {
            /** Format: date-time */
            readonly createdAt?: string;
            readonly createdBy?: string;
            readonly delegateeUserId?: string;
            readonly delegatorUserId?: string;
            /** Format: int64 */
            readonly id?: number;
            readonly processKeys?: string;
            readonly reason?: string;
            readonly roleIds?: string;
            readonly scope?: string;
            readonly status?: string;
            /** Format: int64 */
            readonly tenantId?: number;
            /** Format: date-time */
            readonly validFrom?: string;
            /** Format: date-time */
            readonly validTo?: string;
        };
        readonly DirectoryEntryView: {
            readonly email?: string;
            /** Format: int64 */
            readonly employeeId?: number;
            readonly empNo?: string;
            /** Format: date */
            readonly hireDate?: string;
            readonly leader?: boolean;
            readonly mobile?: string;
            readonly name?: string;
            /** Format: int64 */
            readonly orgId?: number;
            readonly orgName?: string;
            readonly orgPath?: string;
            readonly positionName?: string;
            readonly status?: string;
            /** Format: int64 */
            readonly syncSeq?: number;
            readonly userId?: string;
        };
        readonly DocType: {
            readonly code?: string;
            readonly driver?: string;
            readonly name?: string;
            readonly rule?: string;
            readonly schema?: components["schemas"]["JsonNode"];
            /** Format: int32 */
            readonly version?: number;
        };
        readonly DocView: {
            readonly amount?: number;
            readonly approverChain?: readonly string[];
            readonly bizType?: string;
            /** Format: date-time */
            readonly createdAt?: string;
            readonly days?: number;
            readonly docNo?: string;
            readonly formData?: {
                readonly [key: string]: Record<string, never>;
            };
            /** Format: int64 */
            readonly id?: number;
            readonly status?: string;
            readonly summary?: string;
            readonly title?: string;
        };
        readonly DraftDoc: {
            readonly body?: string;
            readonly direction: string;
            readonly docType?: string;
            readonly secrecy?: string;
            readonly sourceOrg?: string;
            readonly title: string;
            readonly urgency?: string;
        };
        readonly Elevate: {
            /** Format: int32 */
            readonly hours?: number;
            readonly reason: string;
            /** Format: int64 */
            readonly roleId: number;
        };
        readonly ElevationDecision: {
            readonly reason?: string;
        };
        readonly ElevationView: {
            /** Format: date-time */
            readonly grantedAt?: string;
            /** Format: int64 */
            readonly grantId?: number;
            readonly reason?: string;
            /** Format: int64 */
            readonly remainingMs?: number;
            readonly roleCode?: string;
            /** Format: int64 */
            readonly roleId?: number;
            readonly roleName?: string;
            /** Format: date-time */
            readonly validTo?: string;
        };
        readonly EmployeeView: {
            readonly avatar?: string;
            readonly email?: string;
            readonly employmentType?: string;
            readonly empNo?: string;
            readonly enName?: string;
            /** Format: date */
            readonly hireDate?: string;
            /** Format: int64 */
            readonly id?: number;
            readonly name?: string;
            /** Format: int64 */
            readonly primaryOrgId?: number;
            readonly primaryOrgName?: string;
            readonly primaryOrgPath?: string;
            /** Format: int64 */
            readonly primaryPositionId?: number;
            readonly primaryPositionName?: string;
            readonly status?: string;
            readonly userId?: string;
        };
        readonly Grant: {
            readonly grantType?: string;
            readonly includeDescendants?: boolean;
            readonly reason?: string;
            /** Format: int64 */
            readonly roleId: number;
            readonly scopeOrgIds?: readonly number[];
            readonly scopeType?: string;
            readonly subjectId: string;
            readonly subjectType: string;
            /** Format: date-time */
            readonly validFrom?: string;
            /** Format: date-time */
            readonly validTo?: string;
        };
        readonly GrantBalance: {
            readonly days: number;
            readonly leaveTypeCode: string;
            readonly period: string;
            readonly userId: string;
        };
        readonly GrantRecord: {
            readonly approvalInstanceId?: string;
            /** Format: date-time */
            readonly grantedAt?: string;
            readonly grantedBy?: string;
            readonly grantType?: string;
            /** Format: int64 */
            readonly id?: number;
            readonly includeDescendants?: boolean;
            readonly reason?: string;
            /** Format: date-time */
            readonly revokedAt?: string;
            readonly revokedBy?: string;
            readonly revokeReason?: string;
            /** Format: int64 */
            readonly roleId?: number;
            readonly scopeOrgIds?: string;
            readonly scopeType?: string;
            readonly source?: string;
            readonly subjectId?: string;
            readonly subjectType?: string;
            /** Format: int64 */
            readonly tenantId?: number;
            /** Format: date-time */
            readonly validFrom?: string;
            /** Format: date-time */
            readonly validTo?: string;
        };
        readonly InviteVisitor: {
            readonly company?: string;
            readonly name: string;
            readonly phone?: string;
            /** Format: date-time */
            readonly visitAt: string;
        };
        readonly JsonNode: Record<string, never>;
        readonly KbAccessExplain: {
            readonly allowed?: boolean;
            readonly decidedBy?: string;
            readonly detail?: string;
            /** Format: int64 */
            readonly docId?: number;
            readonly level?: string;
            readonly userId?: string;
        };
        readonly KbDocView: {
            readonly body?: string;
            /** Format: int64 */
            readonly folderId?: number;
            /** Format: int64 */
            readonly id?: number;
            readonly ownerId?: string;
            readonly summary?: string;
            readonly title?: string;
            /** Format: date-time */
            readonly updatedAt?: string;
            /** Format: int32 */
            readonly version?: number;
        };
        readonly LeaveBalanceView: {
            readonly availableDays?: number;
            readonly frozenDays?: number;
            readonly leaveTypeCode?: string;
            readonly leaveTypeName?: string;
            readonly period?: string;
            readonly totalDays?: number;
            readonly usedDays?: number;
        };
        readonly LeaveRequestView: {
            readonly applicantName?: string;
            readonly approverChain?: readonly string[];
            /** Format: date-time */
            readonly createdAt?: string;
            readonly days?: number;
            /** Format: date */
            readonly endDate?: string;
            /** Format: int64 */
            readonly id?: number;
            readonly leaveTypeCode?: string;
            readonly leaveTypeName?: string;
            /** Format: int64 */
            readonly orgId?: number;
            readonly orgPath?: string;
            readonly processInstanceId?: string;
            readonly reason?: string;
            readonly requestNo?: string;
            /** Format: date */
            readonly startDate?: string;
            readonly status?: string;
            readonly userId?: string;
        };
        readonly LeaveType: {
            readonly code?: string;
            /** Format: int64 */
            readonly id?: number;
            readonly maxDays?: number;
            readonly name?: string;
            readonly needBalance?: boolean;
            readonly paid?: boolean;
            readonly status?: string;
        };
        readonly MenuNode: {
            readonly code?: string;
            readonly icon?: string;
            readonly name?: string;
            readonly route?: string;
            /** Format: int32 */
            readonly sortOrder?: number;
        };
        readonly MoveOrg: {
            /** Format: int64 */
            readonly newParentId?: number;
        };
        readonly MyApplicationView: {
            readonly amount?: number;
            readonly bizType?: string;
            /** Format: date-time */
            readonly createdAt?: string;
            readonly days?: number;
            readonly docNo?: string;
            readonly status?: string;
            readonly summary?: string;
            readonly title?: string;
        };
        readonly MyPermissions: {
            readonly dataScope?: string;
            readonly delegators?: readonly string[];
            readonly elevatedCodes?: readonly string[];
            /** Format: int64 */
            readonly employeeId?: number;
            readonly menus?: readonly components["schemas"]["MenuNode"][];
            readonly moduleScope?: {
                readonly [key: string]: string;
            };
            readonly permCodes?: readonly string[];
            /** Format: int64 */
            readonly primaryOrgId?: number;
            readonly primaryOrgPath?: string;
            readonly scopePrefixes?: readonly string[];
            readonly userId?: string;
            readonly username?: string;
            /** Format: int64 */
            readonly version?: number;
        };
        readonly OrgSnapshotView: {
            readonly allOrgIds?: readonly number[];
            /** Format: date */
            readonly asOf?: string;
            readonly managerUserId?: string;
            readonly pathPrefixes?: readonly string[];
            /** Format: int64 */
            readonly primaryOrgId?: number;
            readonly primaryOrgName?: string;
            readonly primaryOrgPath?: string;
            readonly userId?: string;
        };
        readonly OrgTreeNodeView: {
            readonly code?: string;
            /** Format: int32 */
            readonly depth?: number;
            /** Format: int64 */
            readonly id?: number;
            readonly leaderUserId?: string;
            /** Format: int32 */
            readonly memberCount?: number;
            readonly name?: string;
            /** Format: int64 */
            readonly parentId?: number;
            readonly path?: string;
            /** Format: int32 */
            readonly sortOrder?: number;
            readonly status?: string;
            readonly type?: string;
        };
        readonly OrgUnitView: {
            readonly code?: string;
            /** Format: int32 */
            readonly depth?: number;
            readonly deputyLeaderUserId?: string;
            /** Format: int64 */
            readonly id?: number;
            readonly leaderUserId?: string;
            readonly name?: string;
            /** Format: int64 */
            readonly parentId?: number;
            readonly path?: string;
            readonly shortName?: string;
            /** Format: int32 */
            readonly sortOrder?: number;
            readonly status?: string;
            readonly type?: string;
        };
        readonly PermissionCondition: {
            /** Format: date-time */
            readonly createdAt?: string;
            readonly createdBy?: string;
            /** Format: date-time */
            readonly deletedAt?: string;
            readonly description?: string;
            readonly enabled?: boolean;
            readonly expression?: string;
            /** Format: int64 */
            readonly id?: number;
            /** Format: int64 */
            readonly permissionId?: number;
            /** Format: int64 */
            readonly roleId?: number;
            /** Format: int64 */
            readonly tenantId?: number;
            /** Format: date-time */
            readonly updatedAt?: string;
            readonly updatedBy?: string;
        };
        readonly PermissionView: {
            readonly code?: string;
            readonly enabled?: boolean;
            readonly icon?: string;
            /** Format: int64 */
            readonly id?: number;
            readonly module?: string;
            readonly name?: string;
            readonly remark?: string;
            readonly requireElevation?: boolean;
            readonly route?: string;
            /** Format: int32 */
            readonly sortOrder?: number;
            readonly type?: string;
        };
        readonly PunchResult: {
            readonly accepted?: boolean;
            readonly duplicate?: boolean;
            readonly message?: string;
        };
        readonly ReplaceRoleInheritance: {
            readonly inheritedRoleIds: readonly number[];
            /** Format: int32 */
            readonly version: number;
        };
        readonly ReplaceRolePermissions: {
            readonly permissionIds: readonly number[];
            /** Format: int32 */
            readonly version: number;
        };
        readonly RequestSupply: {
            /** Format: int32 */
            readonly qty?: number;
            /** Format: int64 */
            readonly supplyId: number;
        };
        readonly ResultDocView: {
            /** Format: int32 */
            readonly code?: number;
            readonly data?: components["schemas"]["DocView"];
            readonly message?: string;
            readonly traceId?: string;
        };
        readonly ResultEmployeeView: {
            /** Format: int32 */
            readonly code?: number;
            readonly data?: components["schemas"]["EmployeeView"];
            readonly message?: string;
            readonly traceId?: string;
        };
        readonly ResultKbAccessExplain: {
            /** Format: int32 */
            readonly code?: number;
            readonly data?: components["schemas"]["KbAccessExplain"];
            readonly message?: string;
            readonly traceId?: string;
        };
        readonly ResultKbDocView: {
            /** Format: int32 */
            readonly code?: number;
            readonly data?: components["schemas"]["KbDocView"];
            readonly message?: string;
            readonly traceId?: string;
        };
        readonly ResultLeaveRequestView: {
            /** Format: int32 */
            readonly code?: number;
            readonly data?: components["schemas"]["LeaveRequestView"];
            readonly message?: string;
            readonly traceId?: string;
        };
        readonly ResultListAssetView: {
            /** Format: int32 */
            readonly code?: number;
            readonly data?: readonly components["schemas"]["AssetView"][];
            readonly message?: string;
            readonly traceId?: string;
        };
        readonly ResultListAssignmentView: {
            /** Format: int32 */
            readonly code?: number;
            readonly data?: readonly components["schemas"]["AssignmentView"][];
            readonly message?: string;
            readonly traceId?: string;
        };
        readonly ResultListBookingView: {
            /** Format: int32 */
            readonly code?: number;
            readonly data?: readonly components["schemas"]["BookingView"][];
            readonly message?: string;
            readonly traceId?: string;
        };
        readonly ResultListDelegation: {
            /** Format: int32 */
            readonly code?: number;
            readonly data?: readonly components["schemas"]["Delegation"][];
            readonly message?: string;
            readonly traceId?: string;
        };
        readonly ResultListDirectoryEntryView: {
            /** Format: int32 */
            readonly code?: number;
            readonly data?: readonly components["schemas"]["DirectoryEntryView"][];
            readonly message?: string;
            readonly traceId?: string;
        };
        readonly ResultListDocType: {
            /** Format: int32 */
            readonly code?: number;
            readonly data?: readonly components["schemas"]["DocType"][];
            readonly message?: string;
            readonly traceId?: string;
        };
        readonly ResultListDocView: {
            /** Format: int32 */
            readonly code?: number;
            readonly data?: readonly components["schemas"]["DocView"][];
            readonly message?: string;
            readonly traceId?: string;
        };
        readonly ResultListElevationView: {
            /** Format: int32 */
            readonly code?: number;
            readonly data?: readonly components["schemas"]["ElevationView"][];
            readonly message?: string;
            readonly traceId?: string;
        };
        readonly ResultListGrantRecord: {
            /** Format: int32 */
            readonly code?: number;
            readonly data?: readonly components["schemas"]["GrantRecord"][];
            readonly message?: string;
            readonly traceId?: string;
        };
        readonly ResultListKbDocView: {
            /** Format: int32 */
            readonly code?: number;
            readonly data?: readonly components["schemas"]["KbDocView"][];
            readonly message?: string;
            readonly traceId?: string;
        };
        readonly ResultListLeaveBalanceView: {
            /** Format: int32 */
            readonly code?: number;
            readonly data?: readonly components["schemas"]["LeaveBalanceView"][];
            readonly message?: string;
            readonly traceId?: string;
        };
        readonly ResultListLeaveType: {
            /** Format: int32 */
            readonly code?: number;
            readonly data?: readonly components["schemas"]["LeaveType"][];
            readonly message?: string;
            readonly traceId?: string;
        };
        readonly ResultListLong: {
            /** Format: int32 */
            readonly code?: number;
            readonly data?: readonly number[];
            readonly message?: string;
            readonly traceId?: string;
        };
        readonly ResultListMapStringObject: {
            /** Format: int32 */
            readonly code?: number;
            readonly data?: readonly {
                readonly [key: string]: Record<string, never>;
            }[];
            readonly message?: string;
            readonly traceId?: string;
        };
        readonly ResultListMyApplicationView: {
            /** Format: int32 */
            readonly code?: number;
            readonly data?: readonly components["schemas"]["MyApplicationView"][];
            readonly message?: string;
            readonly traceId?: string;
        };
        readonly ResultListOrgTreeNodeView: {
            /** Format: int32 */
            readonly code?: number;
            readonly data?: readonly components["schemas"]["OrgTreeNodeView"][];
            readonly message?: string;
            readonly traceId?: string;
        };
        readonly ResultListPermissionCondition: {
            /** Format: int32 */
            readonly code?: number;
            readonly data?: readonly components["schemas"]["PermissionCondition"][];
            readonly message?: string;
            readonly traceId?: string;
        };
        readonly ResultListPermissionView: {
            /** Format: int32 */
            readonly code?: number;
            readonly data?: readonly components["schemas"]["PermissionView"][];
            readonly message?: string;
            readonly traceId?: string;
        };
        readonly ResultListRoleSummary: {
            /** Format: int32 */
            readonly code?: number;
            readonly data?: readonly components["schemas"]["RoleSummary"][];
            readonly message?: string;
            readonly traceId?: string;
        };
        readonly ResultListRoleView: {
            /** Format: int32 */
            readonly code?: number;
            readonly data?: readonly components["schemas"]["RoleView"][];
            readonly message?: string;
            readonly traceId?: string;
        };
        readonly ResultListRoomView: {
            /** Format: int32 */
            readonly code?: number;
            readonly data?: readonly components["schemas"]["RoomView"][];
            readonly message?: string;
            readonly traceId?: string;
        };
        readonly ResultListRow: {
            /** Format: int32 */
            readonly code?: number;
            readonly data?: readonly components["schemas"]["Row"][];
            readonly message?: string;
            readonly traceId?: string;
        };
        readonly ResultListString: {
            /** Format: int32 */
            readonly code?: number;
            readonly data?: readonly string[];
            readonly message?: string;
            readonly traceId?: string;
        };
        readonly ResultListSupplyView: {
            /** Format: int32 */
            readonly code?: number;
            readonly data?: readonly components["schemas"]["SupplyView"][];
            readonly message?: string;
            readonly traceId?: string;
        };
        readonly ResultListTodoView: {
            /** Format: int32 */
            readonly code?: number;
            readonly data?: readonly components["schemas"]["TodoView"][];
            readonly message?: string;
            readonly traceId?: string;
        };
        readonly ResultListUserGroup: {
            /** Format: int32 */
            readonly code?: number;
            readonly data?: readonly components["schemas"]["UserGroup"][];
            readonly message?: string;
            readonly traceId?: string;
        };
        readonly ResultListUserGroupMember: {
            /** Format: int32 */
            readonly code?: number;
            readonly data?: readonly components["schemas"]["UserGroupMember"][];
            readonly message?: string;
            readonly traceId?: string;
        };
        readonly ResultListVisitorView: {
            /** Format: int32 */
            readonly code?: number;
            readonly data?: readonly components["schemas"]["VisitorView"][];
            readonly message?: string;
            readonly traceId?: string;
        };
        readonly ResultLong: {
            /** Format: int32 */
            readonly code?: number;
            /** Format: int64 */
            readonly data?: number;
            readonly message?: string;
            readonly traceId?: string;
        };
        readonly ResultMapStringBoolean: {
            /** Format: int32 */
            readonly code?: number;
            readonly data?: {
                readonly [key: string]: boolean;
            };
            readonly message?: string;
            readonly traceId?: string;
        };
        readonly ResultMapStringInteger: {
            /** Format: int32 */
            readonly code?: number;
            readonly data?: {
                readonly [key: string]: number;
            };
            readonly message?: string;
            readonly traceId?: string;
        };
        readonly ResultMapStringObject: {
            /** Format: int32 */
            readonly code?: number;
            readonly data?: {
                readonly [key: string]: Record<string, never>;
            };
            readonly message?: string;
            readonly traceId?: string;
        };
        readonly ResultMapStringString: {
            /** Format: int32 */
            readonly code?: number;
            readonly data?: {
                readonly [key: string]: string;
            };
            readonly message?: string;
            readonly traceId?: string;
        };
        readonly ResultMyPermissions: {
            /** Format: int32 */
            readonly code?: number;
            readonly data?: components["schemas"]["MyPermissions"];
            readonly message?: string;
            readonly traceId?: string;
        };
        readonly ResultOrgSnapshotView: {
            /** Format: int32 */
            readonly code?: number;
            readonly data?: components["schemas"]["OrgSnapshotView"];
            readonly message?: string;
            readonly traceId?: string;
        };
        readonly ResultOrgUnitView: {
            /** Format: int32 */
            readonly code?: number;
            readonly data?: components["schemas"]["OrgUnitView"];
            readonly message?: string;
            readonly traceId?: string;
        };
        readonly ResultPunchResult: {
            /** Format: int32 */
            readonly code?: number;
            readonly data?: components["schemas"]["PunchResult"];
            readonly message?: string;
            readonly traceId?: string;
        };
        readonly ResultRoleDetail: {
            /** Format: int32 */
            readonly code?: number;
            readonly data?: components["schemas"]["RoleDetail"];
            readonly message?: string;
            readonly traceId?: string;
        };
        readonly ResultVoid: {
            /** Format: int32 */
            readonly code?: number;
            readonly data?: Record<string, never>;
            readonly message?: string;
            readonly traceId?: string;
        };
        readonly RoleDetail: {
            readonly builtin?: boolean;
            readonly code?: string;
            readonly defaultScope?: string;
            readonly directPermissionIds?: readonly number[];
            readonly effectivePermissionIds?: readonly number[];
            /** Format: int64 */
            readonly grantCount?: number;
            /** Format: int64 */
            readonly id?: number;
            readonly inheritedRoleIds?: readonly number[];
            readonly name?: string;
            readonly remark?: string;
            readonly status?: string;
            readonly type?: string;
            /** Format: int32 */
            readonly version?: number;
        };
        readonly RoleSummary: {
            readonly builtin?: boolean;
            readonly code?: string;
            readonly defaultScope?: string;
            /** Format: int64 */
            readonly directPermissionCount?: number;
            /** Format: int64 */
            readonly effectivePermissionCount?: number;
            /** Format: int64 */
            readonly grantCount?: number;
            /** Format: int64 */
            readonly id?: number;
            /** Format: int64 */
            readonly inheritedRoleCount?: number;
            readonly name?: string;
            readonly remark?: string;
            readonly status?: string;
            readonly type?: string;
            /** Format: int32 */
            readonly version?: number;
        };
        readonly RoleView: {
            readonly builtin?: boolean;
            readonly code?: string;
            readonly defaultScope?: string;
            /** Format: int64 */
            readonly id?: number;
            readonly name?: string;
            readonly remark?: string;
            readonly type?: string;
        };
        readonly RoomView: {
            /** Format: int32 */
            readonly capacity?: number;
            readonly code?: string;
            readonly equipment?: string;
            /** Format: int64 */
            readonly id?: number;
            readonly location?: string;
            readonly name?: string;
            readonly status?: string;
        };
        readonly Row: {
            /** Format: date-time */
            readonly decidedAt?: string;
            readonly decidedBy?: string;
            readonly decisionReason?: string;
            /** Format: int64 */
            readonly grantId?: number;
            /** Format: int32 */
            readonly hours?: number;
            /** Format: int64 */
            readonly id?: number;
            readonly reason?: string;
            /** Format: date-time */
            readonly requestedAt?: string;
            readonly requesterId?: string;
            readonly roleCode?: string;
            /** Format: int64 */
            readonly roleId?: number;
            readonly roleName?: string;
            readonly status?: string;
            /** Format: int64 */
            readonly tenantId?: number;
        };
        readonly SetReportingLine: {
            /** Format: int64 */
            readonly managerEmployeeId: number;
            readonly type: string;
            /** Format: date */
            readonly validFrom?: string;
        };
        readonly SetRoleEnabled: {
            readonly enabled: boolean;
            /** Format: int32 */
            readonly version: number;
        };
        readonly ShareKb: {
            readonly level?: string;
            /** Format: int64 */
            readonly resourceId: number;
            readonly resourceType: string;
            readonly subjectId?: string;
            readonly subjectType: string;
        };
        readonly SubmitDoc: {
            readonly bizType?: string;
            readonly formData?: {
                readonly [key: string]: Record<string, never>;
            };
        };
        readonly SubmitLeave: {
            readonly days: number;
            /** Format: date */
            readonly endDate: string;
            readonly leaveTypeCode: string;
            readonly reason?: string;
            /** Format: date */
            readonly startDate: string;
        };
        readonly SupplyView: {
            readonly code?: string;
            /** Format: int64 */
            readonly id?: number;
            readonly name?: string;
            /** Format: int32 */
            readonly stock?: number;
            readonly unit?: string;
        };
        readonly TodoView: {
            readonly applicantName?: string;
            readonly applicantUserId?: string;
            readonly assigneeUserId?: string;
            readonly bizType?: string;
            readonly candidateGroup?: string;
            /** Format: date-time */
            readonly createdAt?: string;
            /** Format: date-time */
            readonly dueAt?: string;
            /** Format: int64 */
            readonly id?: number;
            /** Format: int64 */
            readonly instanceId?: number;
            /** Format: int64 */
            readonly orgId?: number;
            readonly orgPath?: string;
            readonly processDefinitionKey?: string;
            readonly processInstanceId?: string;
            readonly state?: string;
            readonly summary?: string;
            readonly taskId?: string;
            readonly title?: string;
        };
        readonly TransferEmployee: {
            readonly asLeader?: boolean;
            /** Format: date */
            readonly effectiveDate: string;
            readonly reason?: string;
            /** Format: int64 */
            readonly targetOrgId: number;
            /** Format: int64 */
            readonly targetPositionId?: number;
        };
        readonly UpdateEmployee: {
            readonly avatar?: string;
            /** Format: date */
            readonly birthday?: string;
            readonly email?: string;
            readonly enName?: string;
            readonly gender?: string;
            readonly mobile?: string;
            readonly name?: string;
        };
        readonly UpdateGroup: {
            readonly description?: string;
            readonly name: string;
        };
        readonly UpdateOrg: {
            readonly costCenter?: string;
            readonly deputyLeaderUserId?: string;
            readonly leaderUserId?: string;
            readonly name?: string;
            readonly remark?: string;
            readonly shortName?: string;
            /** Format: int32 */
            readonly sortOrder?: number;
            readonly type?: string;
        };
        readonly UpdateRole: {
            readonly defaultScope: string;
            readonly name: string;
            readonly remark?: string;
            /** Format: int32 */
            readonly version: number;
        };
        readonly UserGroup: {
            readonly code?: string;
            /** Format: date-time */
            readonly createdAt?: string;
            readonly createdBy?: string;
            readonly description?: string;
            /** Format: int64 */
            readonly id?: number;
            readonly name?: string;
            readonly status?: string;
            /** Format: int64 */
            readonly tenantId?: number;
            /** Format: date-time */
            readonly updatedAt?: string;
            readonly updatedBy?: string;
        };
        readonly UserGroupMember: {
            /** Format: date-time */
            readonly createdAt?: string;
            readonly createdBy?: string;
            /** Format: int64 */
            readonly groupId?: number;
            /** Format: int64 */
            readonly id?: number;
            /** Format: date-time */
            readonly revokedAt?: string;
            readonly revokedBy?: string;
            /** Format: int64 */
            readonly tenantId?: number;
            readonly userId?: string;
            /** Format: date-time */
            readonly validFrom?: string;
            /** Format: date-time */
            readonly validTo?: string;
        };
        readonly ValidateAbac: {
            readonly expression: string;
        };
        readonly VisitorView: {
            readonly company?: string;
            readonly hostId?: string;
            /** Format: int64 */
            readonly id?: number;
            /** Format: date-time */
            readonly leaveAt?: string;
            readonly name?: string;
            readonly status?: string;
            /** Format: date-time */
            readonly visitAt?: string;
        };
    };
    responses: never;
    parameters: never;
    requestBodies: never;
    headers: never;
    pathItems: never;
};
export type $defs = Record<string, never>;
export interface operations {
    readonly assets: {
        readonly parameters: {
            readonly query?: {
                readonly limit?: number;
                readonly status?: string;
            };
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultListAssetView"];
                };
            };
        };
    };
    readonly giveBack: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly id: number;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultVoid"];
                };
            };
        };
    };
    readonly claim: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["ClaimAsset"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultVoid"];
                };
            };
        };
    };
    readonly rooms: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultListRoomView"];
                };
            };
        };
    };
    readonly bookings: {
        readonly parameters: {
            readonly query: {
                readonly from: string;
                readonly to: string;
            };
            readonly header?: never;
            readonly path: {
                readonly roomId: number;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultListBookingView"];
                };
            };
        };
    };
    readonly book: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["BookRoom"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultMapStringObject"];
                };
            };
        };
    };
    readonly cancelBooking: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly id: number;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultVoid"];
                };
            };
        };
    };
    readonly supplies: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultListSupplyView"];
                };
            };
        };
    };
    readonly requestSupply: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["RequestSupply"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultVoid"];
                };
            };
        };
    };
    readonly vehicles: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultListMapStringObject"];
                };
            };
        };
    };
    readonly bookVehicle: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["BookVehicle"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultMapStringObject"];
                };
            };
        };
    };
    readonly myVisitors: {
        readonly parameters: {
            readonly query?: {
                readonly limit?: number;
            };
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultListVisitorView"];
                };
            };
        };
    };
    readonly invite: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["InviteVisitor"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultMapStringObject"];
                };
            };
        };
    };
    readonly checkIn: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly id: number;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultVoid"];
                };
            };
        };
    };
    readonly checkOut: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly id: number;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultVoid"];
                };
            };
        };
    };
    readonly stats: {
        readonly parameters: {
            readonly query?: {
                readonly date?: string;
            };
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultMapStringObject"];
                };
            };
        };
    };
    readonly computeDaily: {
        readonly parameters: {
            readonly query?: {
                readonly date?: string;
            };
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultMapStringObject"];
                };
            };
        };
    };
    readonly myDay: {
        readonly parameters: {
            readonly query?: {
                readonly date?: string;
            };
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultListMapStringObject"];
                };
            };
        };
    };
    readonly punch: {
        readonly parameters: {
            readonly query?: {
                readonly deviceId?: string;
                readonly lat?: number;
                readonly lng?: number;
                readonly source?: string;
                readonly type?: string;
            };
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultPunchResult"];
                };
            };
        };
    };
    readonly listKb: {
        readonly parameters: {
            readonly query?: {
                readonly limit?: number;
            };
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultListKbDocView"];
                };
            };
        };
    };
    readonly createKb: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["CreateKbDoc"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultMapStringObject"];
                };
            };
        };
    };
    readonly readKb: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly id: number;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultKbDocView"];
                };
            };
        };
    };
    readonly updateKb: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly id: number;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["CreateKbDoc"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultVoid"];
                };
            };
        };
    };
    readonly explain_1: {
        readonly parameters: {
            readonly query?: {
                readonly userId?: string;
            };
            readonly header?: never;
            readonly path: {
                readonly id: number;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultKbAccessExplain"];
                };
            };
        };
    };
    readonly authorizer: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultMapStringString"];
                };
            };
        };
    };
    readonly share: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["ShareKb"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultVoid"];
                };
            };
        };
    };
    readonly search_1: {
        readonly parameters: {
            readonly query?: {
                readonly keyword?: string;
                readonly limit?: number;
                readonly status?: string;
            };
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultListDocView"];
                };
            };
        };
    };
    readonly draft: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["DraftDoc"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultMapStringObject"];
                };
            };
        };
    };
    readonly archive: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly id: number;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultVoid"];
                };
            };
        };
    };
    readonly issue: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly id: number;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultMapStringObject"];
                };
            };
        };
    };
    readonly status: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultMapStringObject"];
                };
            };
        };
    };
    readonly submit_1: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["SubmitDoc"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultDocView"];
                };
            };
        };
    };
    readonly mine: {
        readonly parameters: {
            readonly query?: {
                readonly bizType?: string;
                readonly limit?: number;
            };
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultListDocView"];
                };
            };
        };
    };
    readonly types_1: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultListDocType"];
                };
            };
        };
    };
    readonly submit: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["SubmitLeave"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultLeaveRequestView"];
                };
            };
        };
    };
    readonly get_1: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly requestNo: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultLeaveRequestView"];
                };
            };
        };
    };
    readonly myBalances: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultListLeaveBalanceView"];
                };
            };
        };
    };
    readonly grant_1: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["GrantBalance"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultVoid"];
                };
            };
        };
    };
    readonly types: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultListLeaveType"];
                };
            };
        };
    };
    readonly myTodos: {
        readonly parameters: {
            readonly query?: {
                readonly limit?: number;
            };
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultListTodoView"];
                };
            };
        };
    };
    readonly complete: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly taskId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["CompleteTask"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultVoid"];
                };
            };
        };
    };
    readonly count_1: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultMapStringObject"];
                };
            };
        };
    };
    readonly myApplications: {
        readonly parameters: {
            readonly query?: {
                readonly bizType?: string;
                readonly limit?: number;
            };
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultListMyApplicationView"];
                };
            };
        };
    };
    readonly list_3: {
        readonly parameters: {
            readonly query?: {
                readonly permissionId?: number;
                readonly roleId?: number;
            };
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultListPermissionCondition"];
                };
            };
        };
    };
    readonly create_4: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["AbacCondition"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultLong"];
                };
            };
        };
    };
    readonly update_4: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly id: number;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["AbacCondition"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultVoid"];
                };
            };
        };
    };
    readonly delete_1: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly id: number;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultVoid"];
                };
            };
        };
    };
    readonly enabled_2: {
        readonly parameters: {
            readonly query: {
                readonly enabled: boolean;
            };
            readonly header?: never;
            readonly path: {
                readonly id: number;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultVoid"];
                };
            };
        };
    };
    readonly validate: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["ValidateAbac"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultMapStringBoolean"];
                };
            };
        };
    };
    readonly bench: {
        readonly parameters: {
            readonly query: {
                readonly iterations?: number;
                readonly permCode?: string;
                readonly userId: string;
            };
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultMapStringObject"];
                };
            };
        };
    };
    readonly cacheStats_1: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultMapStringObject"];
                };
            };
        };
    };
    readonly explain: {
        readonly parameters: {
            readonly query: {
                readonly permCode?: string;
                readonly userId: string;
            };
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultMapStringObject"];
                };
            };
        };
    };
    readonly preview: {
        readonly parameters: {
            readonly query: {
                readonly userId: string;
            };
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultMapStringObject"];
                };
            };
        };
    };
    readonly reclaim: {
        readonly parameters: {
            readonly query?: {
                readonly limit?: number;
            };
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultMapStringObject"];
                };
            };
        };
    };
    readonly why: {
        readonly parameters: {
            readonly query: {
                readonly permCode: string;
                readonly userId: string;
            };
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultMapStringObject"];
                };
            };
        };
    };
    readonly myDelegations: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultListDelegation"];
                };
            };
        };
    };
    readonly delegate: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["Delegate"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultLong"];
                };
            };
        };
    };
    readonly revokeDelegation: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly id: number;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultVoid"];
                };
            };
        };
    };
    readonly elevationRequests: {
        readonly parameters: {
            readonly query?: {
                readonly limit?: number;
                readonly status?: string;
            };
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultListRow"];
                };
            };
        };
    };
    readonly approveElevation: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly id: number;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: {
            readonly content: {
                readonly "application/json": components["schemas"]["ElevationDecision"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultLong"];
                };
            };
        };
    };
    readonly rejectElevation: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly id: number;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: {
            readonly content: {
                readonly "application/json": components["schemas"]["ElevationDecision"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultVoid"];
                };
            };
        };
    };
    readonly myElevationRequests: {
        readonly parameters: {
            readonly query?: {
                readonly limit?: number;
                readonly status?: string;
            };
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultListRow"];
                };
            };
        };
    };
    readonly elevate: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["Elevate"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultLong"];
                };
            };
        };
    };
    readonly myElevations: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultListElevationView"];
                };
            };
        };
    };
    readonly list_2: {
        readonly parameters: {
            readonly query: {
                readonly subjectId: string;
                readonly subjectType: string;
            };
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultListGrantRecord"];
                };
            };
        };
    };
    readonly grant: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["Grant"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultLong"];
                };
            };
        };
    };
    readonly revoke: {
        readonly parameters: {
            readonly query?: {
                readonly reason?: string;
            };
            readonly header?: never;
            readonly path: {
                readonly grantId: number;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultVoid"];
                };
            };
        };
    };
    readonly list_1: {
        readonly parameters: {
            readonly query?: {
                readonly status?: string;
            };
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultListUserGroup"];
                };
            };
        };
    };
    readonly create_3: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["CreateGroup"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultLong"];
                };
            };
        };
    };
    readonly update_3: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly id: number;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["UpdateGroup"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultVoid"];
                };
            };
        };
    };
    readonly enabled_1: {
        readonly parameters: {
            readonly query: {
                readonly enabled: boolean;
            };
            readonly header?: never;
            readonly path: {
                readonly id: number;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultVoid"];
                };
            };
        };
    };
    readonly members: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly id: number;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultListUserGroupMember"];
                };
            };
        };
    };
    readonly addMembers: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly id: number;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["AddGroupMembers"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultMapStringInteger"];
                };
            };
        };
    };
    readonly removeMember: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly id: number;
                readonly userId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultVoid"];
                };
            };
        };
    };
    readonly catalog: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultListPermissionView"];
                };
            };
        };
    };
    readonly list: {
        readonly parameters: {
            readonly query?: {
                readonly keyword?: string;
                readonly status?: string;
            };
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultListRoleSummary"];
                };
            };
        };
    };
    readonly create_2: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["CreateRole"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultLong"];
                };
            };
        };
    };
    readonly detail: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly id: number;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultRoleDetail"];
                };
            };
        };
    };
    readonly update_2: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly id: number;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["UpdateRole"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultVoid"];
                };
            };
        };
    };
    readonly delete: {
        readonly parameters: {
            readonly query: {
                readonly version: number;
            };
            readonly header?: never;
            readonly path: {
                readonly id: number;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultVoid"];
                };
            };
        };
    };
    readonly copy: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly id: number;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["CopyRole"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultLong"];
                };
            };
        };
    };
    readonly enabled: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly id: number;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["SetRoleEnabled"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultVoid"];
                };
            };
        };
    };
    readonly inheritance: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly id: number;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["ReplaceRoleInheritance"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultVoid"];
                };
            };
        };
    };
    readonly permissions: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly id: number;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["ReplaceRolePermissions"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultVoid"];
                };
            };
        };
    };
    readonly roles: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultListRoleView"];
                };
            };
        };
    };
    readonly myRoles: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultListRoleView"];
                };
            };
        };
    };
    readonly myPermissions: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultMyPermissions"];
                };
            };
        };
    };
    readonly search: {
        readonly parameters: {
            readonly query?: {
                readonly keyword?: string;
                readonly limit?: number;
            };
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultListDirectoryEntryView"];
                };
            };
        };
    };
    readonly count: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultMapStringObject"];
                };
            };
        };
    };
    readonly delta: {
        readonly parameters: {
            readonly query?: {
                readonly since?: number;
                readonly size?: number;
            };
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultMapStringObject"];
                };
            };
        };
    };
    readonly page: {
        readonly parameters: {
            readonly query?: {
                readonly cursor?: number;
                readonly keyword?: string;
                readonly size?: number;
            };
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultMapStringObject"];
                };
            };
        };
    };
    readonly create_1: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["CreateEmployee"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultLong"];
                };
            };
        };
    };
    readonly update_1: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly employeeId: number;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["UpdateEmployee"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultVoid"];
                };
            };
        };
    };
    readonly addAssignment: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly employeeId: number;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["AddAssignment"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultLong"];
                };
            };
        };
    };
    readonly leave: {
        readonly parameters: {
            readonly query?: {
                readonly leaveDate?: string;
            };
            readonly header?: never;
            readonly path: {
                readonly employeeId: number;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultVoid"];
                };
            };
        };
    };
    readonly setReportingLine: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly employeeId: number;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["SetReportingLine"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultVoid"];
                };
            };
        };
    };
    readonly transfer: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly employeeId: number;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["TransferEmployee"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultVoid"];
                };
            };
        };
    };
    readonly closeAssignment: {
        readonly parameters: {
            readonly query?: {
                readonly validTo?: string;
            };
            readonly header?: never;
            readonly path: {
                readonly assignmentId: number;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultVoid"];
                };
            };
        };
    };
    readonly byUser: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly userId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultEmployeeView"];
                };
            };
        };
    };
    readonly asOf: {
        readonly parameters: {
            readonly query: {
                readonly date: string;
            };
            readonly header?: never;
            readonly path: {
                readonly userId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultOrgSnapshotView"];
                };
            };
        };
    };
    readonly assignments: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly userId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultListAssignmentView"];
                };
            };
        };
    };
    readonly managerChain: {
        readonly parameters: {
            readonly query?: {
                readonly maxLevel?: number;
            };
            readonly header?: never;
            readonly path: {
                readonly userId: string;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultListString"];
                };
            };
        };
    };
    readonly create: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["CreateOrg"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultLong"];
                };
            };
        };
    };
    readonly get: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly orgId: number;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultOrgUnitView"];
                };
            };
        };
    };
    readonly update: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly orgId: number;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["UpdateOrg"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultVoid"];
                };
            };
        };
    };
    readonly dissolve: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly orgId: number;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultVoid"];
                };
            };
        };
    };
    readonly ancestors: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly orgId: number;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultListLong"];
                };
            };
        };
    };
    readonly descendants: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly orgId: number;
            };
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultListLong"];
                };
            };
        };
    };
    readonly move: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path: {
                readonly orgId: number;
            };
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": components["schemas"]["MoveOrg"];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultVoid"];
                };
            };
        };
    };
    readonly cacheStats: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultMapStringObject"];
                };
            };
        };
    };
    readonly consistency: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultMapStringObject"];
                };
            };
        };
    };
    readonly pathPrefixes: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody: {
            readonly content: {
                readonly "application/json": readonly number[];
            };
        };
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultListString"];
                };
            };
        };
    };
    readonly tree: {
        readonly parameters: {
            readonly query?: {
                readonly maxDepth?: number;
                readonly rootId?: number;
            };
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultListOrgTreeNodeView"];
                };
            };
        };
    };
    readonly approval: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultListMapStringObject"];
                };
            };
        };
    };
    readonly attendance: {
        readonly parameters: {
            readonly query?: {
                readonly days?: number;
            };
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultListMapStringObject"];
                };
            };
        };
    };
    readonly auditQuery: {
        readonly parameters: {
            readonly query?: {
                readonly action?: string;
                readonly actorId?: string;
                readonly deniedOnly?: boolean;
                readonly limit?: number;
            };
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultListMapStringObject"];
                };
            };
        };
    };
    readonly auditFlush: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultVoid"];
                };
            };
        };
    };
    readonly auditStats: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultMapStringObject"];
                };
            };
        };
    };
    readonly headcount: {
        readonly parameters: {
            readonly query?: {
                readonly limit?: number;
                readonly orgPath?: string;
            };
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultListMapStringObject"];
                };
            };
        };
    };
    readonly overview: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultMapStringObject"];
                };
            };
        };
    };
    readonly ping: {
        readonly parameters: {
            readonly query?: never;
            readonly header?: never;
            readonly path?: never;
            readonly cookie?: never;
        };
        readonly requestBody?: never;
        readonly responses: {
            /** @description OK */
            readonly 200: {
                headers: {
                    readonly [name: string]: unknown;
                };
                content: {
                    readonly "*/*": components["schemas"]["ResultMapStringObject"];
                };
            };
        };
    };
}
