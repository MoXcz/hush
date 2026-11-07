# Sync on your network

The desktop app syncs history, tags, activities and sorting rules with a paired phone, directly over
the local network. Nothing goes through the internet.

## What the computer needs

- **`avahi-daemon` running.** The desktop announces itself over mDNS so the phone can find it.
- **Port 47447/TCP open** (another free port if that one is taken), plus mDNS on 5353/UDP. The
  desktop only listens and announces itself while a phone is paired or the Sync screen shows a code.

If a firewall is on, allow both, limited to your home network (replace `192.168.1.0/24` with
yours):

```sh
# ufw
sudo ufw allow from 192.168.1.0/24 to any port 47447 proto tcp
sudo ufw allow from 192.168.1.0/24 to any port 5353 proto udp

# firewalld: put your network in the home zone
sudo firewall-cmd --permanent --zone=home --add-source=192.168.1.0/24
sudo firewall-cmd --permanent --zone=home --add-port=47447/tcp --add-service=mdns
sudo firewall-cmd --reload
```

## Pairing

1. On the computer, open Hush, then **Settings → Sync** ("Pair with your phone"). It shows a code
   like `K7QM-2F9X-HC4D`, valid for two minutes.
2. On the phone, open Hush, then **Settings → Sync** ("Pair with your computer"). Type the code and
   tap **Pair**. On Android 17 Hush first asks for permission to find devices on your network.
3. Both devices then show the other one's name, and the first sync runs right away.

After that the phone syncs whenever you open Hush (at most once a minute) and about every hour in
the background while it's on unmetered Wi-Fi; the computer only needs Hush open. **Unpair** forgets
the other device on this device only; pairing again replaces the old pairing.

## When it doesn't connect

- Guest networks that isolate devices from each other block discovery.
- Check that the computer is announcing itself: `avahi-browse -rt _hush._tcp` (only while paired or
  while the pairing code shows).

## Where the desktop keeps its data

Settings in `~/.config/hush`, history and sync keys in `~/.local/share/hush` (or
`$XDG_CONFIG_HOME/hush` and `$XDG_DATA_HOME/hush`). Both are private to your user, and uninstalling
leaves them in place.
