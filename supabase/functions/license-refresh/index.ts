// POST license-refresh: see _shared/service.ts, refresh.
import { serve } from "../_shared/http.ts";
import { refresh } from "../_shared/service.ts";

serve(refresh);
