// POST license-activate: see _shared/service.ts, activate.
import { serve } from "../_shared/http.ts";
import { activate } from "../_shared/service.ts";

serve(activate);
