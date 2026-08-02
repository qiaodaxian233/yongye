# 武器整模替换前备份(m442,作者:「以前的武器先备份」)

本目录 = **m441 收工时(c0f01fd)** 的全部武器资产快照:6 个模型 JSON(含原作者
credit「Done by Pramanix」,署名随文件保留)+ 全部武器贴图(m441 动态化后的版本)+ mcmeta。

## 一键还原(还原剑客为例)
```
cp docs/backup/weapons_pre_m442/models/class_weapon_swordsman.json src/main/resources/assets/yongye/models/item/
cp docs/backup/weapons_pre_m442/textures/class_weapon_swordsman.png docs/backup/weapons_pre_m442/textures/class_weapon_swordsman.png.mcmeta src/main/resources/assets/yongye/textures/item/
```

## 更早的版本(git 里都在)
- m441 动态化**之前**的静态原图:`git checkout b995c22 -- src/main/resources/assets/yongye/textures/item/`
- m210 黑白化**之前**的彩色原版:`git checkout 5bbc7bd -- <路径>`(DEVLOG m210 口径)
