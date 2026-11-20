// POST play-verify: see _shared/play.ts, playVerify.
import { serve } from "../_shared/http.ts";
import { playVerify } from "../_shared/play.ts";

serve((config, userId, body) => playVerify(config, userId, body));
