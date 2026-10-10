// POST account-delete: see _shared/service.ts, deleteAccount.
import { deleteAuthUser, serve } from "../_shared/http.ts";
import { deleteAccount } from "../_shared/service.ts";

serve((config, userId) => deleteAccount(config, userId, deleteAuthUser));
