# How to Enable the GitHub Wiki

## Step 1: Enable Wiki in Repository Settings

1. Go to https://github.com/NeaBouli/stealth
2. Click **Settings** (gear icon)
3. Scroll to **Features** section
4. Check **Wikis**
5. Click **Save**

## Step 2: Create the Wiki Pages

1. Go to https://github.com/NeaBouli/stealth/wiki
2. Click **"Create the first page"**
3. Set the title to: `Home`
4. Copy the content from `docs/WIKI/Home.md`
5. Click **"Save Page"**

## Step 3: Add Remaining Pages

For each file in `docs/WIKI/`, create a new wiki page:

| File | Wiki Page Name |
|------|---------------|
| `Home.md` | Home (already created) |
| `Installation-Guide.md` | Installation Guide |
| `User-Manual.md` | User Manual |
| `FAQ.md` | FAQ |
| `Security-Design.md` | Security Design |
| `Security-Audit.md` | Security Audit |
| `Encryption-Architecture.md` | Encryption Architecture |
| `Architecture.md` | Architecture |
| `Build-Instructions.md` | Build Instructions |
| `API-Documentation.md` | API Documentation |
| `Roadmap.md` | Roadmap |
| `Changelog.md` | Changelog |
| `Known-Issues.md` | Known Issues |

For each page:
1. Click **"New Page"** in the wiki sidebar
2. Set the title (without `.md` extension, use spaces instead of hyphens)
3. Copy the content from the corresponding `docs/WIKI/` file
4. Click **"Save Page"**

## Step 4: Alternative — Clone Wiki via Git

The wiki can also be managed via git:

```bash
# Clone the wiki repo
git clone https://github.com/NeaBouli/stealth.wiki.git
cd stealth.wiki

# Copy all wiki pages
cp ../stealth/docs/WIKI/*.md .

# Push
git add . && git commit -m "Add complete documentation" && git push
```

> This is the fastest way to add all 13 pages at once.
