// Google Play's Real-time developer notifications, pushed by Pub/Sub: see _shared/play.ts, playNotification.
import { servePubSub } from "../_shared/http.ts";
import { playNotification } from "../_shared/play.ts";

servePubSub((config, notification) => playNotification(config, notification));
