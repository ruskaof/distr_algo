# Отчет по лаборатороной работе №1

Были реализованые следующие алгоритмы:

- [Extendible Hash Table](./app/src/main/java/com/ruskaof/algorithm/ExtendibleHashTable.java)
- [Perfect Hash](./app/src/main/java/com/ruskaof/algorithm/PerfectHash.java)
- [LSH Hash Table](./app/src/main/java/com/ruskaof/algorithm/LshHashTable.java)

Сложности алгоритмов:

## Extendible Hash Table

Вставка амортизированно работает за O(1)

![Extendible Hash Table](./graphs/hashes/extendible_hash_put_new.png)

Получение: O(1)

![Extendible Hash Table Get](./graphs/hashes/extendible_hash_get.png)

## Perfect Hash

Создание в матожидании работает за O(n)

![Perfect Hash](./graphs/hashes/perfect_hash_build.png)

Получение: O(1)

![Perfect Hash Get](./graphs/hashes/perfect_hash_get.png)

## LSH Hash Table

Получение всех бакетов O(n)

![LSH Hash Table](./graphs/hashes/lsh_read.png)

Создание O(n)

![LSH Hash Table Build](./graphs/hashes/lsh_build.png)

Вставка O(1)

![LSH Hash Table Add](./graphs/hashes/lsh_put.png)
