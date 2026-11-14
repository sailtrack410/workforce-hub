const fs = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');

async function writeLocalUpload(uploadPath, content) {
  if (typeof uploadPath !== 'string' || !Array.isArray(content)) {
    throw new TypeError('上传参数无效');
  }
  const root = path.resolve(os.homedir(), '.workforce-hub', 'files', 'uploads');
  const target = path.resolve(uploadPath);
  if (target !== root && !target.startsWith(`${root}${path.sep}`)) {
    throw new Error('上传路径不在本地文件目录中');
  }
  const data = Buffer.from(content);
  await fs.mkdir(path.dirname(target), { recursive: true });
  await fs.writeFile(target, data);
}

module.exports = { writeLocalUpload };
