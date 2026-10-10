// POST device-release: see _shared/service.ts, release.
import { serve } from "../_shared/http.ts";
import { release } from "../_shared/service.ts";

serve(release);
