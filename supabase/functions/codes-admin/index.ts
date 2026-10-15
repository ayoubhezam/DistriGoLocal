// POST codes-admin: the owner's « Administration » screen, see _shared/service.ts, codesAdmin.
import { serve } from "../_shared/http.ts";
import { codesAdmin } from "../_shared/service.ts";

serve(codesAdmin);
